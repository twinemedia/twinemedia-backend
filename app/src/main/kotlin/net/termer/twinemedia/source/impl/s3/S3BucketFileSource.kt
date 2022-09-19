package net.termer.twinemedia.source.impl.s3

import io.reactiverse.awssdk.VertxSdkClient
import io.vertx.core.AsyncResult
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpClient
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.core.http.httpClientOptionsOf
import io.vertx.kotlin.core.http.requestOptionsOf
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twinemedia.source.*
import net.termer.twinemedia.source.config.SourceNotConfiguredException
import net.termer.twinemedia.source.impl.s3.signing.AWS4SignerForAuthorizationHeader
import net.termer.twinemedia.util.*
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import java.net.URI
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletionException
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.text.Charsets.UTF_8

@DelicateCoroutinesApi
class S3BucketFileSource(override val vertx: Vertx): StatefulFileSource {
	companion object {
		/**
		 * The maximum file size in bytes that can be uploaded to S3 through a single HTTP PUT request.
		 * Current set to 50MB (50,000,000).
		 * @since 2.0.0
		 */
		const val MAX_HTTP_PUT_UPLOAD_SIZE = 50_000_000

		/**
		 * Characters that can be used in AWS file key strings.
		 * @since 2.0.0
		 */
		const val AWS_SAFE_KEY_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ/!-_.*'()"
	}

	// Date format for X-Amz-Date header
	private val amzDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
	private val clock = Clock.systemUTC()

	/**
	 * Throws [SourceNotConfiguredException] if not configured
	 * @throws SourceNotConfiguredException if not configured
	 */
	private fun failIfNotConfigured() {
		if(config.accessKey == null)
			throw SourceNotConfiguredException("S3BucketFileSource has not been configured")
	}

	/**
	 * Returns a file's download URL (may or may not require special permission to access)
	 * @param key The file's key
	 * @return The file's download URL
	 * @since 1.5.0
	 */
	fun keyToUrl(key: String) = "${config.endpoint}/${config.bucketName}/$key"

	private var client: S3AsyncClient? = null
	private var httpClient: HttpClient? = null
	private val fs = vertx.fileSystem()

	override val config = S3BucketFileSourceConfig()
	override val lock = ConcurrentLock()

	override suspend fun startup() {
		// Set session properties for authentication
		System.setProperty("aws.accessKeyId", config.accessKey!!)
		System.setProperty("aws.secretAccessKey", config.secretKey!!)

		// Create S3 async client
		client = VertxSdkClient.withVertx(
			S3AsyncClient.builder()
				.endpointOverride(URI(config.endpoint!!))
				.region(Region.of(config.region!!.uppercase()))
				.credentialsProvider(AWSCredentialProvider(config.accessKey!!, config.secretKey!!)),
			vertx.orCreateContext
		).build()

		httpClient = vertx.createHttpClient(httpClientOptionsOf(
			keepAlive = false,
			maxPoolSize = 100
		))
	}

	override suspend fun shutdown() {
		// Close resources
		client?.close()
	}

	override suspend fun fileExists(key: String): Boolean {
		failIfNotConfigured()

		return try {
			// Get file metadata
			getFile(key)

			// If no exception was thrown, the file exists
			true
		} catch(e: FileSourceFileNotFoundException) {
			false
		}
	}

	override suspend fun listFiles(): Array<FileSourceFile> {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val files = ArrayList<FileSourceFile>()

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
					files.add(FileSourceFile(
						key = t.key(),
						url = keyToUrl(t.key()),
						size = t.size(),
						modifiedTs = t.lastModified().atOffset(ZoneOffset.UTC),
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
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun getFile(key: String): FileSourceFile {
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

				FileSourceFile(
					key = key,
					url = keyToUrl(key),
					mime = res.contentType(),
					size = res.contentLength(),
					modifiedTs = res.lastModified().atOffset(ZoneOffset.UTC),
					hash = res.eTag()
				)
			} catch(e: CompletionException) {
				if(e.cause is NoSuchKeyException)
					throw FileSourceFileNotFoundException("File with key \"$key\" does not exist")
				else
					throw wrapThrowableAsFileSourceException(e)
			}
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun openReadStream(key: String, offset: Long, length: Long): FileSource.StreamAndFile {
		failIfNotConfigured()

		try {
			// Create request options
			val path = '/'+config.bucketName!!+'/'+key
			val reqOps = requestOptionsOf(
				absoluteURI = config.endpoint!!.stripTrailingSlash()+path,
			)
			val host = reqOps.host

			// HTTP headers to send
			val amzDate = amzDateFormat.format(ZonedDateTime.now(clock))
			val headers = hashMapOf(
				"X-Amz-Date" to amzDate
			)

			// Work out range
			if(offset > -1 || length > -1) {
				val start = offset.coerceAtLeast(0)
				val end = if(length > -1) start+length-1 else null
				val endStr = end?.toString().orEmpty()
				val rangeStr = "bytes=$start-$endStr"

				headers["Range"] = rangeStr
			}

			// Process and sort headers
			val headerKeys = headers.keys.toTypedArray()
			for((i, name) in headerKeys.withIndex()) {
				val lower = name.lowercase()
				if(!headers.containsKey(lower)) {
					val value = headers[name]
					headers.remove(name)
					headers[lower] = value
				}
				headerKeys[i] = lower
			}
			headerKeys.sortWith(String.CASE_INSENSITIVE_ORDER)

			// Add headers to request options
			for((name, value) in headers)
				reqOps.addHeader(name, value)
			reqOps.addHeader("host", host)

			// Calculate signature
			val authHeader = vertx.executeBlocking {
				val signer = AWS4SignerForAuthorizationHeader(
					endpointUrl = URL(config.endpoint!!+path),
					httpMethod = "GET",
					serviceName = "s3",
					regionName = config.region!!
				)
				it.complete(signer.computeSignature(
					headers = headers,
					queryParameters = HashMap(),
					bodyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // Blank
					awsAccessKey = config.accessKey!!,
					awsSecretKey = config.secretKey!!
				))
			}.await()

			// Add signature to HTTP request in Authorization header
			reqOps.addHeader("Authorization", authHeader)

			// Fire HTTP request and check if it's successful
			val req = httpClient!!.request(reqOps).await()
			val res = req.send().await()
			if(res.statusCode() !in 200..299) {
				// Try to read response to understand the issue
				val body = res.body().await().toString(UTF_8)
				throw FileSourceException("S3 returned bad status code ${res.statusCode()} and body:\n$body")
			}

			// Collect file data
			val resHeaders = res.headers()
			val url = keyToUrl(key)
			var size: Long? = null
			val contentLength = if(resHeaders.contains("content-length"))
				try {
					resHeaders["content-length"].toInt()
				} catch(e: NumberFormatException) { null }
			else null

			if(contentLength != null) {
				val realOffset = offset.coerceAtLeast(0)

				size = when {
					length < 0 -> realOffset + contentLength
					realOffset + length > contentLength -> realOffset + contentLength
					else -> null
				}
			}
			val modifiedTs = if(resHeaders.contains("last-modified"))
				try {
					gmtToOffsetDateTime(resHeaders["last-modified"])
				} catch(e: Exception) { null }
			else null
			val hash = if(resHeaders.contains("etag"))
				resHeaders["etag"]
			else null
			val mime = if(resHeaders.contains("content-type"))
				resHeaders["content-type"]
			else null

			val file = FileSourceFile(
				key,
				url = url,
				mime = mime,
				size = size,
				modifiedTs = modifiedTs,
				hash = hash
			)

			// Open and send stream
			val conn = req.connection()
			val stream = HttpResponseReadStream(res, conn)
			return FileSource.StreamAndFile(stream, file)
		} catch(e: Throwable) {
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun openWriteStream(key: String, length: Long): WriteStream<Buffer> {
		failIfNotConfigured()

		if(length < 0)
			throw FileSourceException("Must provide length of stream")

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
			throw wrapThrowableAsFileSourceException(e)
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
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun downloadFile(key: String, pathOnDisk: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val source = openReadStream(key).stream
			val dest = fs.open(pathOnDisk, openOptionsOf(write = true)).await()

			source.pipeTo(dest).await()

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun uploadFile(pathOnDisk: String, key: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			// Get file length
			val size = fs.props(pathOnDisk).await().size()

			// If file is less than 50MB, upload through PUT
			if(size < MAX_HTTP_PUT_UPLOAD_SIZE) {
				val source = fs.open(pathOnDisk, OpenOptions().setRead(true)).await()
				val dest = openWriteStream(key, size)

				source.pipeTo(dest).await()
			} else {
				// Upload file via multipart if it's more 50MB or larger to avoid issues

				// Create multipart upload
				val createReq = CreateMultipartUploadRequest.builder()
					.bucket(config.bucketName)
					.key(key)
					.build()
				val uplId = client!!.createMultipartUpload(createReq).await().uploadId()

				// Determine how many pieces the file will need to be cut into
				val maxPartSize = MAX_HTTP_PUT_UPLOAD_SIZE
				val partCount = ceil(size.toDouble()/maxPartSize).roundToInt()
				val partSize = ceil(size.toDouble()/partCount).roundToInt()

				try {
					// Upload each part
					val finishedParts = ArrayList<CompletedPart>()
					for(part in 1..partCount) {
						val currentSize = if(part >= partCount)
							(size - (partSize * (part - 1))).toInt()
						else
							partSize

						// Create upload request
						val uplReq = UploadPartRequest.builder()
							.bucket(config.bucketName)
							.key(key)
							.uploadId(uplId)
							.partNumber(part)
							.contentLength(currentSize.toLong())
							.build()

						// Create write and read stream
						val writeStream = WriteStreamAsyncBodyHandler()
						val readStream = fs.open(pathOnDisk, openOptionsOf(read = true)).await()
							.pause()
						readStream
							.setReadLength(currentSize.toLong())
							.setReadPos((part - 1) * partSize.toLong())

						// Begin upload
						val upload = client!!.uploadPart(uplReq, writeStream)

						// Pipe file piece to upload request body
						readStream.pipeTo(writeStream).await()

						// Wait for upload to finish
						val res = upload.await()

						// Add finished part to list of parts
						finishedParts.add(CompletedPart.builder()
							.eTag(res.eTag())
							.partNumber(part)
							.build())
					}

					// Complete upload
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
				} catch(e: Exception) {
					try {
						// Abort upload
						val abortReq = AbortMultipartUploadRequest.builder()
							.bucket(config.bucketName)
							.key(key)
							.uploadId(uplId)
							.build()
						client!!.abortMultipartUpload(abortReq).await()
					} catch(e: Exception) {
						lock.deleteLock(lockId)
						throw wrapThrowableAsFileSourceException(e)
					}

					lock.deleteLock(lockId)
					throw wrapThrowableAsFileSourceException(e)
				}
			}

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override fun supportsReadPosition() = true

	override fun filenameToKey(filename: String): String {
		return filenameToFilenameWithUnixTimestamp(filename.withOnlyChars(AWS_SAFE_KEY_CHARS))
			.replace(' ', '_')
	}

	override suspend fun getRemainingStorage(): Long? = null
}