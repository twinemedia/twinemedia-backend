package net.termer.twinemedia.source.impl.s3

import io.reactiverse.awssdk.VertxSdkClient
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.source.*
import net.termer.twinemedia.source.config.SourceNotConfiguredException
import net.termer.twinemedia.util.ConcurrentLock
import net.termer.twinemedia.util.await
import net.termer.twinemedia.util.filenameToFilenameWithUnixTimestamp
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import java.net.URI
import java.time.ZoneOffset
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@DelicateCoroutinesApi
class S3BucketMediaSource: StatefulMediaSource {
	/**
	 * Throws SourceNotConfiguredException if not configured
	 * @throws SourceNotConfiguredException if not configured
	 */
	private fun failIfNotConfigured() {
		if(config.accessKey == null)
			throw SourceNotConfiguredException("S3BucketMediaSource has not been configured")
	}

	/**
	 * Returns a file's download URL (may or may not require special permission to access)
	 * @param key The file's key
	 * @return The file's download URL
	 * @since 1.5.0
	 */
	fun keyToUrl(key: String) = "${config.endpoint}/${config.bucketName}/$key"

	private val config = S3BucketMediaSourceConfig()
	private val lock = ConcurrentLock()
	private var client: S3AsyncClient? = null
	private val fs = vertx().fileSystem()

	override suspend fun startup() {
		// Set session properties for authentication
		System.setProperty("aws.accessKeyId", config.accessKey!!)
		System.setProperty("aws.secretAccessKey", config.secretKey!!)

		// Create S3 async client
		client = VertxSdkClient.withVertx(
				S3AsyncClient.builder()
						.endpointOverride(URI(config.endpoint!!))
						.region(Region.of(config.region!!.toUpperCase()))
						.credentialsProvider(AWSCredentialProvider(config.accessKey!!, config.secretKey!!)),
				vertx().orCreateContext
		).build()
	}

	override suspend fun shutdown() {
		// Close resources
		client?.close()
	}

	override fun getConfig() = config

	override fun getLock() = lock

	override suspend fun fileExists(key: String): Boolean {
		failIfNotConfigured()

		return try {
			// Get file metadata
			getFile(key)

			// If no exception was thrown, the file exists
			true
		} catch(e: MediaSourceFileNotFoundException) {
			false
		}
	}

	override suspend fun listFiles(): Array<MediaSourceFile> {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val files = ArrayList<MediaSourceFile>()

			// Create listing request
			val req = ListObjectsV2Request.builder()
					.bucket(config.bucketName)
					.maxKeys(1000)
					.build()

			// Execute request
			val res = client!!.listObjectsV2Paginator(req)

			// Await completion
			val promise = Promise.promise<Unit>()

			// Subscribe to results and add them as they come in
			res.contents().subscribe(object : Subscriber<S3Object> {
				private var subscription: Subscription? = null

				override fun onSubscribe(s: Subscription) {
					subscription = s
					s.request(1000)
				}

				override fun onNext(t: S3Object) {
					files.add(MediaSourceFile(
							key = t.key(),
							url = keyToUrl(t.key()),
							size = t.size(),
							modifiedOn = t.lastModified().atOffset(ZoneOffset.UTC),
							hash = t.eTag()
					))
					subscription!!.request(1000)
				}

				override fun onError(t: Throwable?) {
					promise.fail(t)
				}

				override fun onComplete() {
					promise.complete()
				}
			})

			promise.future().await()

			lock.deleteLock(lockId)

			return files.toTypedArray()
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun getFile(key: String): MediaSourceFile {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			// Create metadata request
			val req = HeadObjectRequest.builder()
					.bucket(config.bucketName!!)
					.key(key)
					.build()

			return try {
				// Execute request
				val res = client!!.headObject(req).await()

				lock.deleteLock(lockId)

				MediaSourceFile(
						key = key,
						url = keyToUrl(key),
						mime = res.contentType(),
						size = res.contentLength(),
						modifiedOn = res.lastModified().atOffset(ZoneOffset.UTC),
						hash = res.eTag()
				)
			} catch(e: CompletionException) {
				if(e.cause is NoSuchKeyException)
					throw MediaSourceFileNotFoundException("File $key does not exist")
				else
					throw wrapThrowableAsMediaSourceException(e)
			}
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun openReadStream(key: String, offset: Long, length: Long): MediaSource.StreamAndFile {
		failIfNotConfigured()

		try {
			val readStream = ReadStreamAsyncResponseTransformer<GetObjectResponse>()

			// Start building request
			val req = GetObjectRequest.builder()
					.bucket(config.bucketName)
					.key(key)

			// Work out range
			if(offset > -1 || length > -1) {
				val start = offset.coerceAtLeast(0)
				val end = if(length > -1) start+length-1 else null
				val endStr = end?.toString().orEmpty()
				val rangeStr = "bytes=$start-$endStr"

				req.range(rangeStr)
			}

			// Begin getting object
			client!!.getObject(req.build(), readStream)

			// Wait until initial response is received
			readStream.pause()
			var file: MediaSourceFile? = null
			val promise = Promise.promise<GetObjectResponse>()
			readStream.setResponseHandler {
				val realOffset = offset.coerceAtLeast(0)
				val resLen = it.contentLength()
				file = MediaSourceFile(
						key,
						url = keyToUrl(key),
						size = when {
							length < 0 -> realOffset + resLen
							realOffset + length > resLen -> realOffset + resLen
							else -> null
						},
						modifiedOn = it.lastModified().atOffset(ZoneOffset.UTC),
						hash = it.eTag()
				)
				promise.complete()
			}
			promise.future().await()

			// Send stream
			return MediaSource.StreamAndFile(readStream, file!!)
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun openWriteStream(key: String, length: Long): WriteStream<Buffer> {
		failIfNotConfigured()

		if(length < 0)
			throw MediaSourceException("Must provide length of stream")

		try {
			// Create upload request
			val req = PutObjectRequest.builder()
					.bucket(config.bucketName)
					.key(key)
					.contentLength(length)
					.build()

			// Create WriteStream
			val writeStream = WriteStreamAsyncBodyHandler()

			// Begin upload
			val res = client!!.putObject(req, writeStream)
			val promise = Promise.promise<AsyncResult<Void>>()

			// Require stream to wait for upload to finish
			writeStream.waitForFuture(promise.future())
			res.whenComplete { _, throwable ->
				if(throwable == null)
					promise.complete()
				else
					promise.fail(throwable)
			}

			return writeStream
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun deleteFile(key: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			// Create request
			val req = DeleteObjectRequest.builder()
					.bucket(config.bucketName)
					.key(key)
					.build()

			// Delete
			client!!.deleteObject(req).await()

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun downloadFile(key: String, pathOnDisk: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val source = openReadStream(key).stream
			val dest = fs.open(pathOnDisk, OpenOptions().setWrite(true)).await()

			source.pipeTo(dest).await()

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun uploadFile(pathOnDisk: String, key: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			// Get file length
			val size = fs.props(pathOnDisk).await().size()

			// If file is less than 50MB, upload through PUT
			if(size < 50_000_000) {
				val source = fs.open(pathOnDisk, OpenOptions().setRead(true)).await()
				val dest = openWriteStream(key, size)

				source.pipeTo(dest).await()
			} else {
				// Upload file via multipart if it's more 100MB or larger to avoid issues

				// Create multipart upload
				val createReq = CreateMultipartUploadRequest.builder()
						.bucket(config.bucketName)
						.key(key)
						.build()
				val uplId = client!!.createMultipartUpload(createReq).await().uploadId()

				// Determine how many pieces the file will need to be cut into
				val maxConcurrentUploads = 5
				val maxPieceSize = 50_000_000 // 50MB
				val pieceCount = ceil(size.toDouble()/maxPieceSize).roundToInt()
				val pieceSize = ceil(size.toDouble()/pieceCount).roundToInt()
				val pieces = ConcurrentHashMap<Int, Int>()

				// Figure out each piece's size
				for(i in 1..pieceCount) {
					pieces[i] = if(i >= pieceCount)
						(size - (pieceSize * (i - 1))).toInt()
					else
						pieceSize
				}

				// Wait for all pieces to be uploaded
				val finishedParts = ArrayList<CompletedPart>()
				Future.future<Void> { promise ->
					try {
						var uploading = 0
						var error: Throwable? = null

						fun handleComplete() {
							if(error == null)
								promise.complete()
						}
						suspend fun handleError(e: Throwable) {
							error = e

							try {
								// Abort upload
								val abortReq = AbortMultipartUploadRequest.builder()
										.bucket(config.bucketName)
										.key(key)
										.uploadId(uplId)
										.build()
								client!!.abortMultipartUpload(abortReq).await()

								promise.fail(e)
							} catch(e: Throwable) {
								promise.fail(e)
							}
						}

						suspend fun uploadPart(partNum: Int) {
							if(error == null) {
								uploading++

								// Get size and remove part
								val partSize = pieces[partNum]!!
								pieces.remove(partNum)

								var errored = false
								try {
									// Create upload request
									val uplReq = UploadPartRequest.builder()
											.bucket(config.bucketName)
											.key(key)
											.uploadId(uplId)
											.partNumber(partNum)
											.contentLength(partSize.toLong())
											.build()

									// Create write and read stream
									val writeStream = WriteStreamAsyncBodyHandler()
									val readStream = fs.open(pathOnDisk, OpenOptions().setRead(true)).await()
											.pause()
									readStream
											.setReadLength(partSize.toLong())
											.setReadPos((partNum - 1) * pieceSize.toLong())

									// Begin upload
									val upload = client!!.uploadPart(uplReq, writeStream)

									// Pipe file piece to upload request body
									readStream.pipeTo(writeStream).await()

									// Wait for upload to finish
									val res = upload.await()

									// Add finished part to list of parts
									finishedParts.add(CompletedPart.builder()
											.eTag(res.eTag())
											.partNumber(partNum)
											.build())
								} catch(e: Throwable) {
									uploading--
									errored = true
									e.printStackTrace()
									handleError(e)
								}

								uploading--

								if(!errored) {
									val pieceKeys = pieces.keys().toList().toTypedArray()
									var i = 0

									// Check if any pieces are left
									if(finishedParts.size >= pieceCount) {
										// Finish upload
										handleComplete()
									} else {
										// Upload more pieces
										for(part in pieceKeys) {
											if(i >= maxConcurrentUploads)
												break

											uploadPart(part)

											i++
										}
									}
								}
							}
						}

						for(part in 1..maxConcurrentUploads.coerceAtMost(pieceCount)) {
							if(error == null) {
								GlobalScope.launch(vertx().dispatcher()) {
									try {
										uploadPart(part)
									} catch(e: Throwable) {
										handleError(e)
									}
								}
							}
						}
					} catch(e: Throwable) {
						promise.fail(e)
					}
				}.await()

				// If it got to this point, it's safe to finish the upload
				val completedUpload = CompletedMultipartUpload.builder()
						.parts(finishedParts)
						.build()
				val completeReq = CompleteMultipartUploadRequest.builder()
						.bucket(config.bucketName)
						.key(key)
						.uploadId(uplId)
						.multipartUpload(completedUpload)
						.build()

				client!!.completeMultipartUpload(completeReq).await()
			}

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override fun supportsReadPosition() = true

	override fun filenameToKey(filename: String): String {
		// AWS safe key characters
		val okChars = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '/', '!', '-', '_', '.', '*', '\'', '(', ')')

		val fname = StringBuilder()
		for(char in filename.toCharArray())
			if(okChars.contains(char))
				fname.append(char)

		return filenameToFilenameWithUnixTimestamp(fname.toString()).replace(' ', '_')
	}

	override suspend fun getRemainingStorage(): Long? = null
}