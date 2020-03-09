package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.executeBlockingAwait
import io.vertx.kotlin.core.file.deleteAwait
import io.vertx.kotlin.core.file.existsAwait
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
import net.termer.twinemedia.model.fetchMediaByHash
import net.termer.twinemedia.util.*
import sun.misc.BASE64Encoder
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.security.MessageDigest


/**
 * Sets up all upload routes
 * @since 1.0
 */
fun uploadController() {
    val domain = Twine.domains().byName(config.domain).domain()

    // Accepts media uploads
    // Permissions:
    //  - upload
    // Parameters:
    //  - Provide one file in multipart form data, called "file"
    post("/api/v1/media/upload", domain) { r ->
        r.request().pause()
        GlobalScope.launch(vertx().dispatcher()) {
            if (r.protectWithPermission("upload")) {
                // Check file size header
                val length = r.request().getHeader("content-length").toLong()

                // Upload values
                var filename = "upload"
                var type = "unknown"
                var file = "upload"

                // Generate ID
                val id = generateString(10)

                // File save location
                var saveLoc = "";

                // Only accept upload if not over the size limit
                if(length <= config.max_upload) {
                    var upload = false
                    var error = "No file sent"

                    // Prepare for file uploads
                    r.request().isExpectMultipart = true
                    r.request().uploadHandler { upl ->
                        upload = true

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
                        saveLoc = config.upload_location+id+extension

                        // Stream upload to file
                        upl.streamToFileSystem(saveLoc)

                        // Handle upload errors
                        upl.exceptionHandler {
                            logger.error("Failed to handle upload:")
                            it.printStackTrace()
                            upload = false
                            error = "Internal error"
                            GlobalScope.launch(vertx().dispatcher()) {
                                logger.info("Deleting file $saveLoc")
                                vertx().fileSystem().deleteAwait(saveLoc)
                                logger.info("Deleted")
                            }
                        }
                    }

                    r.request().endHandler {
                        GlobalScope.launch(vertx().dispatcher()) {
                            try {
                                // Calculate file hash
                                val hash = vertx().executeBlockingAwait<String> {
                                    val buffer = ByteArray(8192)
                                    var count : Int
                                    val digest = MessageDigest.getInstance("SHA-256")
                                    val bis = BufferedInputStream(FileInputStream(saveLoc))
                                    while (bis.read(buffer).also { count = it } > 0) {
                                        digest.update(buffer, 0, count)
                                    }
                                    bis.close()

                                    it.complete(BASE64Encoder().encode(digest.digest()))
                                }

                                // Thumbnail file
                                var thumbnail : String? = null

                                // Metadata
                                var meta = JsonObject()

                                // Check if hash was created
                                if(hash == null) {
                                    upload = false
                                    error = "Failed to generate file hash"
                                    logger.info("Deleting file $saveLoc")
                                    vertx().fileSystem().deleteAwait(saveLoc)
                                    logger.info("Deleted")
                                } else {
                                    // Check if a file with the generated hash already exists
                                    val filesRes = fetchMediaByHash(hash)
                                    if(filesRes != null && filesRes.rows.size > 0) {
                                        // Get already uploaded file's filename
                                        file = filesRes.rows[0].getString("media_file")

                                        // Get already uploaded file's thumbnail
                                        thumbnail = filesRes.rows[0].getString("media_thumbnail_file")

                                        // Delete duplicate
                                        logger.info("Deleting file $saveLoc")
                                        vertx().fileSystem().deleteAwait(saveLoc)
                                        logger.info("Deleted")
                                    } else if(type.startsWith("video/")) {
                                        // Generate thumbnail ID
                                        val thumbId = generateString(10)

                                        try {
                                            // Probe file
                                            val probe = probeFile(saveLoc)

                                            if(probe != null) {
                                                // Collect metadata from probe
                                                meta = ffprobeToJsonMeta(probe)

                                                // Generate preview
                                                createVideoThumbnail(saveLoc, (probe.format.duration / 2).toInt(), "${config.upload_location}thumbnails/$thumbId.jpg")
                                                thumbnail = "$thumbId.jpg"
                                            }
                                        } catch(thumbEx : Exception) {
                                            // Failed to generate thumbnail
                                        }
                                    } else if(type.startsWith("image/") || type.startsWith("audio/")) {
                                        // Generate thumbnail ID
                                        val thumbId = generateString(10)

                                        try {
                                            // Generate preview
                                            createImagePreview(saveLoc, "${config.upload_location}thumbnails/$thumbId.jpg")
                                            thumbnail = "$thumbId.jpg"
                                        } catch(thumbEx : Exception) {
                                            // Failed to generate thumbnail
                                        }
                                    }

                                    createMedia(id, filename, length, type, file, r.userId(), hash, thumbnail, meta)
                                }
                            } catch(e : Exception) {
                                logger.error("Failed to create media entry:")
                                e.printStackTrace()
                                upload = false
                                error = "Database error"
                                if(vertx().fileSystem().existsAwait(saveLoc)) {
                                    logger.info("Deleting file $saveLoc")
                                    vertx().fileSystem().deleteAwait(saveLoc)
                                    logger.info("Deleted")
                                }
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