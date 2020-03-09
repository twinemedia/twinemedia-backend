package net.termer.twinemedia.controller

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.post
import net.termer.twine.ServerManager.vertx
import net.termer.twine.Twine.domains
import net.termer.twine.utils.StringFilter
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.createMedia
import net.termer.twinemedia.model.fetchMedia
import net.termer.twinemedia.util.account
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.protectWithPermission
import java.net.URLConnection

/**
 * Setups up all routes for creating child media files
 */
fun mediaChildController() {
    val domain = domains().byName(config.domain).domain()

    post("/api/v1/media/:id/child", domain) { r ->
        val id = r.pathParam("id")
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.child")) {
                try {
                    val mediaRes = fetchMedia(id)

                    // Check if media exists
                    if(mediaRes != null && mediaRes.rows.size > 0) {
                        val media = mediaRes.rows[0]

                        // Check if minimum params are met
                        if(params.contains("bitrate") && params.contains("extension")) {
                            try {
                                val bitrate = params["bitrate"].toLong()
                                val extension = params["extension"].toInt()
                                var width = -1
                                var height = -1

                                // Check if file is a video or audio, or reject
                                if(media.getString("media_mime").startsWith("video/")) {
                                    if(params.contains("width") && params.contains("height")) {
                                        // Set dimensions
                                        width = params["width"].toInt()
                                        height = params["height"].toInt()
                                    } else {
                                        r.error("Must include width and height")
                                        return@launch
                                    }
                                } else if(!media.getString("media_mime").startsWith("audio/")) {
                                    r.error("File must be audio or video")
                                    return@launch
                                }

                                try {
                                    // Generate new media's ID
                                    val newId = generateString(10)

                                    // Generate new media's filename
                                    val filename = "$newId.$extension"

                                    // Utility to infer MIME type from filename
                                    val fileNameMap = URLConnection.getFileNameMap()

                                    // Create media file entry
                                    createMedia(
                                            newId,
                                            media.getString("media_filename"),
                                            -1,
                                            fileNameMap.getContentTypeFor("file.$extension"),
                                            filename,
                                            r.account().getInteger("id"),
                                            "NONE",
                                            null,
                                            JsonObject()
                                    )
                                } catch(e : Exception) {
                                    logger.error("Failed to create media entry:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
                            } catch (e: Exception) {
                                r.error("Invalid parameters")
                            }
                        } else {
                            r.error("Must include bitrate and extension")
                        }
                    } else {
                        r.error("File does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch media file:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }
}