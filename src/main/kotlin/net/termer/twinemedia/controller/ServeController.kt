package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.existsAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.get
import net.termer.twine.ServerManager.vertx
import net.termer.twine.Twine
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.model.fetchMedia
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.sendFileRanged

/**
 * Sets up routes to serve media on
 * @since 1.0
 */
fun serveController() {
    val domain = Twine.domains().byName(config.domain).domain()

    get("/file/:id/*", domain, ::handleFile)
    get("/file/:id", domain, ::handleFile)
    get("/thumbnail/:id/*", domain, ::handleThumbnail)
    get("/thumbnail/:id", domain, ::handleThumbnail)
}

// Handler method for serving files
// Route parameters:
//  - id: String, the alphanumeric ID of the file to download
private fun handleFile(r : RoutingContext) {
    GlobalScope.launch(vertx().dispatcher()) {
        val id = r.pathParam("id")

        // Fetch media info
        val mediaRes = fetchMedia(id)
        // Check if media exists
        if(mediaRes != null && mediaRes.rows.size > 0) {
            val media = mediaRes.rows[0]

            // Locate file location on disk
            val file = config.upload_location+media.getString("media_file")

            if(vertx().fileSystem().existsAwait(file)) {
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
//  - id: String, the alphanumeric ID of the file to download the thumbnail of
private fun handleThumbnail(r : RoutingContext) {
    GlobalScope.launch(vertx().dispatcher()) {
        val id = r.pathParam("id")

        // Fetch media info
        val mediaRes = fetchMedia(id)
        // Check if media exists
        if(mediaRes != null && mediaRes.rows.size > 0) {
            val media = mediaRes.rows[0]

            // Check if media has a thumbnail
            if(media.getBoolean("media_thumbnail")) {
                // Locate file location on disk
                val file = config.upload_location + "thumbnails/" + media.getString("media_thumbnail_file")

                if(vertx().fileSystem().existsAwait(file)) {
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