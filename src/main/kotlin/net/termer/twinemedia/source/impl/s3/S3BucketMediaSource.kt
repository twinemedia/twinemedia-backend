package net.termer.twinemedia.source.impl.s3

import io.reactiverse.awssdk.VertxSdkClient
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.coroutines.await
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.source.*
import net.termer.twinemedia.source.config.SourceNotConfiguredException
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
import kotlin.collections.ArrayList

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
	 * Returns a file's download URL
	 * @param key The file's key
	 * @return The file's download URL
	 * @since 1.5.0
	 */
	fun keyToUrl(key: String) = "${config.endpoint}/${config.bucketName}/$key"

	private val config = S3BucketMediaSourceConfig()
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

			return files.toTypedArray()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun getFile(key: String): MediaSourceFile {
		failIfNotConfigured()

		try {
			// Create metadata request
			val req = HeadObjectRequest.builder()
					.bucket(config.bucketName!!)
					.key(key)
					.build()

			return try {
				// Execute request
				val res = client!!.headObject(req).await()

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
				val end = if(length > -1) start+length else null
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
			readStream.setResponseHandler(Handler {
				file = MediaSourceFile(
						key,
						url = keyToUrl(key),
						size = it.contentLength(),
						modifiedOn = it.lastModified().atOffset(ZoneOffset.UTC),
						hash = it.eTag()
				)
				promise.complete()
			})
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

		try {
			// Create request
			val req = DeleteObjectRequest.builder()
					.bucket(config.bucketName)
					.key(key)
					.build()

			// Delete
			client!!.deleteObject(req).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun downloadFile(key: String, pathOnDisk: String) {
		failIfNotConfigured()

		try {
			val source = openReadStream(key).stream
			val dest = fs.open(pathOnDisk, OpenOptions().setWrite(true)).await()

			source.pipeTo(dest).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun uploadFile(pathOnDisk: String, key: String) {
		failIfNotConfigured()

		try {
			// Get file length
			val size = fs.props(pathOnDisk).await().size()

			val source = fs.open(pathOnDisk, OpenOptions().setRead(true)).await()
			val dest = openWriteStream(key, size)

			source.pipeTo(dest).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override fun supportsReadPosition() = true

	override fun filenameToKey(filename: String) = filenameToFilenameWithUnixTimestamp(filename)

	override suspend fun getRemainingStorage(): Long? = null
}