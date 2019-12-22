package net.termer.twinemedia.controller

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.post
import net.termer.twine.ServerManager.vertx
import net.termer.twine.Twine
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.createMedia
import net.termer.twinemedia.util.*

/**
 * Sets up all upload routes
 * @since 1.0
 */
fun uploadController() {
    val domain = Twine.domains().byName(config.domain).domain()

    // Accepts media uploads
    post("/api/v1/media/upload", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            r.request().pause()
            if (r.protectWithPermission("upload")) {
                // Check file size header
                val length = r.request().getHeader("content-length").toLong()

                // Upload values
                var filename = "upload"
                var type = "unknown"
                var file = "upload"

                // Generate ID
                val id = generateString(10)

                // Only accept upload if not over the size limit
                if(length <= config.max_upload) {
                    var upload = false
                    var error = "No file sent"

                    // Prepare for file uploads
                    r.request().isExpectMultipart = true
                    r.request().uploadHandler { upl ->
                        upload = true

                        // Handle exceptions
                        upl.exceptionHandler {
                            logger.error("Failed to handle upload:")
                            it.printStackTrace()
                            upload = false
                            error = "Internal error"
                        }

                        // Resolve extension
                        var extension = ""
                        if(upl.filename().contains('.')) {
                            val parts = upl.filename().split('.')
                            extension = '.'+parts[parts.size-1]
                        }

                        // Collect info
                        filename = upl.filename()
                        type = upl.contentType()
                        file = id+extension

                        // Stream upload to file
                        upl.streamToFileSystem(config.upload_location+id+extension)
                    }

                    r.request().endHandler {
                        GlobalScope.launch(vertx().dispatcher()) {
                            try {
                                // Fetch info and create media entry
                                createMedia(id, filename, length, type, file)
                            } catch(e : Exception) {
                                logger.error("Failed to create media entry:")
                                e.printStackTrace()
                                upload = false
                                error = "Database error"
                            }

                            // Send response if not already sent
                            if(!r.response().ended()) {
                                if (upload) {
                                    r.success(JsonObject().put("id", id))
                                } else {
                                    r.error(error)
                                }
                            }
                        }
                    }
                } else {
                    r.error("File too large")
                }
            }
            r.request().resume()
        }
    }
}