package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.sendFileRanged

/**
 * Sets up routes to serve media on
 * @since 1.0
 */
fun serveController() {
	for(hostname in appHostnames()) {
		router().get("/download/:id/*").virtualHost(hostname).handler(::handleFile)
		router().get("/download/:id").virtualHost(hostname).handler(::handleFile)
		router().get("/thumbnail/:id/*").virtualHost(hostname).handler(::handleThumbnail)
		router().get("/thumbnail/:id").virtualHost(hostname).handler(::handleThumbnail)
	}
}

// Handler method for serving files
// Route parameters:
//  - id: String, the alphanumeric ID of the file to download
private fun handleFile(r: RoutingContext) {
	GlobalScope.launch(vertx().dispatcher()) {
		val mediaModel = MediaModel()
		val id = r.pathParam("id")

		// Fetch media info
		val mediaRes = mediaModel.fetchMedia(id)
		// Check if media exists
		if(mediaRes.count() > 0) {
			val media = mediaRes.iterator().next()

			// Locate file location on disk
			val file = config.upload_location + media.file

			if(vertx().fileSystem().exists(file).await()) {
				r.sendFileRanged(file)
			} else {
				r.response().statusCode = 404
				r.error("File not found")
			}
		} else {
			r.response().statusCode = 404
			r.error("File not found")
		}
	}
}

// Handler method for serving thumbnails
// Route parameters:
//  - id: String, the alphanumeric ID of the file to download the thumbnail of
private fun handleThumbnail(r: RoutingContext) {
	GlobalScope.launch(vertx().dispatcher()) {
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
				val file = config.upload_location + "thumbnails/" + media.thumbnailFile

				if(vertx().fileSystem().exists(file).await()) {
					r.sendFileRanged(file)
				} else {
					r.response().statusCode = 404
					r.error("File not found")
				}
			} else {
				r.response().statusCode = 404
				r.error("File not found")
			}
		} else {
			r.response().statusCode = 404
			r.error("File not found")
		}
	}
}