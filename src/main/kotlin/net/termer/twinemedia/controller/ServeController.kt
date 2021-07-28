package net.termer.twinemedia.controller

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.coroutines.toChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.Twine
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.Module.Companion.mediaSourceManager
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.source.CloseableReadStream
import net.termer.twinemedia.source.MediaSourceFile
import net.termer.twinemedia.source.MediaSourceFileNotFoundException
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.offsetDateTimeToGMT
import net.termer.twinemedia.util.sendFileRanged
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Sets up routes to serve media on
 * @since 1.0
 */
@DelicateCoroutinesApi
fun serveController() {
	for(hostname in appHostnames()) {
		router().route("/download/:link/*").virtualHost(hostname).handler(::handleFile)
		router().route("/download/:link").virtualHost(hostname).handler(::handleFile)
		router().route("/thumbnail/:id/*").virtualHost(hostname).handler(::handleThumbnail)
		router().route("/thumbnail/:id").virtualHost(hostname).handler(::handleThumbnail)
	}
}

// Handler method for serving files
// Route parameters:
//  - id: String, the alphanumeric ID of the file to download
@DelicateCoroutinesApi
private fun handleFile(r: RoutingContext) {
	GlobalScope.launch(vertx().dispatcher()) {
		val res = r.response()
		val req = r.request()

		suspend fun notFound() {
			res.statusCode = 404
			r.error("File not found")
		}

		// Ignore if not using GET, OPTIONS, or HEAD
		if(req.method() != HttpMethod.GET && req.method() != HttpMethod.OPTIONS && req.method() != HttpMethod.HEAD) {
			r.next()
			return@launch
		}

		val mediaModel = MediaModel()
		val link = r.pathParam("link")
		val id: String

		// Check if link is an encrypted temporary link
		if(link.contains('.')) {
			try {
				// Try to decrypt the link
				val dec = crypt.aesDecrypt(link)

				// Parse the string
				val parts = dec.split(':')
				val time = Instant.ofEpochSecond(parts[0].toLong())
				val fileId = parts[1]

				// Check if link is expired
				if(Instant.now().isAfter(time)) {
					notFound()
					return@launch
				} else {
					id = fileId
				}
			} catch(e: Exception) {
				// Decryption error, not a valid link
				notFound()
				return@launch
			}
		} else {
			id = link
		}

		// Fetch media info
		val mediaRes = mediaModel.fetchMedia(id)
		// Check if media exists
		if(mediaRes.count() > 0) {
			val media = mediaRes.iterator().next()

			// Fetch media's source
			val source = try {
				mediaSourceManager.getSourceInstanceById(media.source)!!
			} catch(e: NullPointerException) {
				logger.error("Tried to fetch media source ID ${media.source}, but it was not registered")
				r.error("Internal error")
				return@launch
			}

			// Lock source
			val srcLock = source.getLock().createLock()

			// Make stream available to error handler if present
			var stream: CloseableReadStream<Buffer>? = null

			try {
				var offset = -1L
				var limit = -1L
				var start = 0L
				var end = -1L

				var usingRange = false

				// Check if source supports range requests
				if(source.supportsReadPosition()) {
					// Advertise range support
					res
							.putHeader("Accept-Ranges", "bytes")
							.putHeader("Vary", "accept-encoding")

					// Try to get offset and limit from range header
					if(req.headers().contains("Range")) {
						usingRange = true

						val rangeStr = req.headers()["Range"].substring(6)
						val parts = rangeStr.split('-')
						start = parts[0].toLong()
						offset = start
						if(!rangeStr.endsWith('-')) {
							end = parts[1].toLong()
							limit = end-start+1
						}
					}
				}

				val file: MediaSourceFile

				// Open stream if this is a GET request, otherwise just fetch file info
				if(req.method() == HttpMethod.GET) {
					val streamRes = source.openReadStream(media.key, offset, limit)
					file = streamRes.file
					stream = streamRes.stream
				} else {
					file = source.getFile(media.key)
				}

				val size = file.size?: media.size
				val mime = if(file.mime == null || file.mime == "binary/octet-stream")
					media.mime
				else
					file.mime

				// Set end to actual end now that we know the real length
				if(end < 0)
					end = size-1

				// Determine content length
				val length = if(usingRange) {
					end-start+1
				} else {
					size
				}
				res.putHeader("Content-Length", "$length")

				// Put range status and headers if it's a ranged request
				if(usingRange) {
					res
							.setStatusCode(206)
							.putHeader("Content-Range", "bytes "+start+'-'+end.coerceAtMost(size-1)+'/'+size)
				}

				// Send caching headers if enabled
				if(Twine.config().getNode("server.static.caching") as Boolean) {
					res
							.putHeader("Date", offsetDateTimeToGMT(OffsetDateTime.now()))
							.putHeader("ETag", file.hash ?: media.hash)
							.putHeader("Last-Modified", offsetDateTimeToGMT(file.modifiedOn?: media.createdOn))
							.putHeader("Last-Modified", offsetDateTimeToGMT(file.createdOn?: media.createdOn))
				}

				// Put content type header
				res.putHeader("Content-Type", mime)

				// Make sure response is not chunked
				res.isChunked = false

				// Pipe stream to response if response isn't closed, and method is GET
				if(req.method() == HttpMethod.GET && !res.closed())
					res.send(stream).await()
				else
					res.end()

			} catch(e: MediaSourceFileNotFoundException) {
				notFound()
			} catch(e: ClosedChannelException) {
				stream?.close()
			} catch(e: IOException) {
				if(e.message != "Connection reset by peer" && e.message != "Broken pipe") {
					logger.error("Failed to send file:")
					e.printStackTrace()
					if(!res.ended())
						r.error("Internal error")
				}

				stream?.close()
			} catch(e: Exception) {
				logger.error("Failed to send file:")
				e.printStackTrace()
				if(!res.ended())
					r.error("Internal error")

				stream?.close()
			}

			// Unlock source
			source.getLock().deleteLock(srcLock)
		} else {
			notFound()
		}
	}
}

// Handler method for serving thumbnails
// Route parameters:
//  - id: String, the alphanumeric ID of the file to download the thumbnail of
@DelicateCoroutinesApi
private fun handleThumbnail(r: RoutingContext) {
	GlobalScope.launch(vertx().dispatcher()) {
		val res = r.response()
		val req = r.request()

		// Ignore if not using GET, OPTIONS, or HEAD
		if(req.method() != HttpMethod.GET && req.method() != HttpMethod.OPTIONS && req.method() != HttpMethod.HEAD) {
			r.next()
			return@launch
		}

		val mediaModel = MediaModel()
		val id = r.pathParam("id")

		// Fetch media info
		val mediaRes = mediaModel.fetchMedia(id)
		// Check if media exists
		if(mediaRes.count() > 0) {
			val media = mediaRes.iterator().next()

			// Check if media has a thumbnail
			if(media.hasThumbnail) {
				// Locate file location on disk
				val file = config.thumbnails_location+media.thumbnailFile

				if(vertx().fileSystem().exists(file).await()) {
					if(req.method() == HttpMethod.GET)
						r.sendFileRanged(file)
					else
						res.end()
				} else {
					res.statusCode = 404
					r.error("File not found")
				}
			} else {
				res.statusCode = 404
				r.error("File not found")
			}
		} else {
			res.statusCode = 404
			r.error("File not found")
		}
	}
}