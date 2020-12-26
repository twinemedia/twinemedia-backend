package net.termer.twinemedia.controller

import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.model.ProcessesModel
import net.termer.twinemedia.util.*
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*


/**
 * Sets up all upload routes
 * @since 1.0
 */
fun uploadController() {
	for(hostname in appHostnames()) {
		// Accepts media uploads
		// Permissions:
		//  - upload
		// Parameters:
		//  - Provide one file in multipart form data, called "file"
		// Headers:
		//  - X-FILE-NAME: URL encoded String, the name to give this file (decoded String must not be more than 256 characters long)
		//  - X-FILE-DESCRIPTION: URL encoded String, the description to give this file (decoded String must not be more than 1024 characters long)
		//  - X-FILE-TAGS: JSON array, tags to give to this file
		//  - X-NO-THUMBNAIL: Bool, whether to disable generating a thumbnail/preview for the uploaded file
		//  - X-NO-PROCESS: Bool, whether to disable running the uploader's process presets on the uploaded file
		router().post("/api/v1/media/upload").virtualHost(hostname).handler { r ->
			r.request().pause()
			val headers = r.request().headers()
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("upload")) {
					val processesModel = ProcessesModel(r.account())
					val mediaModel = MediaModel(r.account())

					// Check file size header
					val length = r.request().getHeader("content-length").toLong()

					// Upload values
					var filename = "upload"
					var type = "unknown"
					var file = "upload"

					// Generate ID
					val id = generateString(10)

					// File save location
					var saveLoc = ""

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
								extension = '.' + parts[parts.size - 1]
							}

							// Collect info
							filename = upl.filename()
							type = upl.contentType()
							file = id + extension
							saveLoc = config.upload_location + id + extension

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
									vertx().fileSystem().delete(saveLoc).await()
									logger.info("Deleted")
								}
							}
						}

						r.request().endHandler {
							GlobalScope.launch(vertx().dispatcher()) {
								delay(100)

								try {
									// Calculate file hash
									val hash = vertx().executeBlocking<String> {
										val file = File(saveLoc)
										it.complete(String(Base64.getEncoder().encode(Files.asByteSource(file).hash(Hashing.sha256()).asBytes())))
									}.await()

									// Thumbnail file
									var thumbnail: String? = null


									// Metadata
									var meta = JsonObject()

									// Check if hash was created
									if(hash == null) {
										upload = false
										error = "Failed to generate file hash"
										logger.info("Deleting file $saveLoc")
										vertx().fileSystem().delete(saveLoc).await()
										logger.info("Deleted")
									} else {
										// Check if a file with the generated hash already exists
										val filesRes = mediaModel.fetchMediaByHash(hash)

										if(filesRes != null && filesRes.rows.size > 0) {
											// Get already uploaded file's filename
											file = filesRes.rows[0].getString("media_file")

											// Ignore thumbnail setting if X-NO-THUMBNAIL is true
											if(!(headers.contains("X-NO-THUMBNAIL") && headers["X-NO-THUMBNAIL"].toLowerCase() == "true")) {
												// Get already uploaded file's thumbnail
												thumbnail = filesRes.rows[0].getString("media_thumbnail_file")
											}

											// Delete duplicate
											logger.info("Deleting file $saveLoc")
											vertx().fileSystem().delete(saveLoc).await()
											logger.info("Deleted")
										} else if(type.startsWith("video/") || type == "image/gif") {
											// Generate thumbnail ID
											val thumbId = generateString(10)

											try {
												// Probe file
												val probe = probeFile(saveLoc)

												if(probe != null) {
													// Collect metadata from probe
													meta = ffprobeToJsonMeta(probe)

													// Don't generate a thumbnail if X-NO-THUMBNAIL is true
													if(!(headers.contains("X-NO-THUMBNAIL") && headers["X-NO-THUMBNAIL"].toLowerCase() == "true")) {
														// Generate preview
														createVideoThumbnail(saveLoc, (probe.format.duration / 2).toInt(), "${config.upload_location}thumbnails/$thumbId.jpg")
														thumbnail = "$thumbId.jpg"
													}
												}
											} catch(thumbEx: Exception) {
												// Failed to generate thumbnail
											}
										} else if(type.startsWith("image/") || type.startsWith("audio/")) {
											// Generate thumbnail ID
											val thumbId = generateString(10)

											// Probe audio for metadata
											if(type.startsWith("audio/")) {
												try {
													// Probe file
													val probe = probeFile(saveLoc)

													if(probe != null) {
														// Collect metadata from probe
														meta = ffprobeToJsonMeta(probe)
													}
												} catch(thumbEx: Exception) {
													// Failed to probe metadata
												}
											}

											// Don't generate a preview if X-NO-THUMBNAIL is true
											if(!(headers.contains("X-NO-THUMBNAIL") && headers["X-NO-THUMBNAIL"].toLowerCase() == "true")) {
												try {
													// Generate preview
													createImagePreview(saveLoc, "${config.upload_location}thumbnails/$thumbId.jpg")
													thumbnail = "$thumbId.jpg"
												} catch(thumbEx: Exception) {
													// Failed to generate preview
												}
											}
										}

										/* Figure out file info */
										var mediaName = filenameToTitle(filename)
										var mediaDesc = ""
										var mediaTags = JsonArray()

										// Handle ffprobe media tags
										if(meta.containsKey("tags")) {
											val tags = meta.getJsonObject("tags")

											if(tags.containsKey("title")) {
												val title = tags.getString("title")
												mediaName = title.toLength(256)
												mediaDesc += "Title: $title\n"
											}
											if(tags.containsKey("author")) {
												val author = tags.getString("author")
												mediaDesc += "Author: $author\n"
											}
											if(tags.containsKey("description")) {
												val description = tags.getString("description")
												mediaDesc += "Description: $description\n"
											}
											if(tags.containsKey("comment")) {
												val comment = tags.getString("comment")
												mediaDesc += "Comment: $comment\n"
											}
											if(tags.containsKey("synopsis")) {
												val synopsis = tags.getString("synopsis")
												mediaDesc += "Synopsis: $synopsis\n"
											}
											if(tags.containsKey("show")) {
												val show = tags.getString("show")
												mediaDesc += "Show: $show\n"
											}
											if(tags.containsKey("season")) {
												val season = tags.getString("season")
												mediaDesc += "Season: $season\n"
											}
											if(tags.containsKey("episode")) {
												val episode = tags.getString("episode")
												mediaDesc += "Episode: $episode\n"
											}
											if(tags.containsKey("artist")) {
												val artist = tags.getString("artist")
												mediaDesc += "Artist: $artist\n"
											}
											if(tags.containsKey("composer")) {
												val composer = tags.getString("composer")
												mediaDesc += "Composer: $composer\n"
											}
											if(tags.containsKey("album")) {
												val album = tags.getString("album")
												mediaDesc += "Album: $album\n"
											}
											if(tags.containsKey("album_artist")) {
												val albumArtist = tags.getString("album_artist")
												mediaDesc += "Album Artist: $albumArtist\n"
											}
											if(tags.containsKey("genre")) {
												val genre = tags.getString("genre")
												mediaDesc += "Genre: $genre\n"
											}
											if(tags.containsKey("label")) {
												val label = tags.getString("label")
												mediaDesc += "Label: $label\n"
											}
											if(tags.containsKey("media")) {
												val mediaType = tags.getString("media")
												mediaDesc += "Media Type: $mediaType\n"
											}
											if(tags.containsKey("track")) {
												val track = tags.getString("track")
												mediaDesc += "Track: $track\n"
											}
											if(tags.containsKey("tracktotal")) {
												val trackTotal = tags.getString("tracktotal")
												mediaDesc += "Track Total: $trackTotal\n"
											}
											if(tags.containsKey("disc")) {
												val disc = tags.getString("disc")
												mediaDesc += "Disc: $disc\n"
											}
											if(tags.containsKey("date")) {
												val date = tags.getString("date")
												mediaDesc += "Date: $date\n"
											}
											if(tags.containsKey("copyright")) {
												val copyright = tags.getString("copyright")
												mediaDesc += "Copyright: $copyright\n"
											}
										}

										// Correct media description
										mediaDesc = mediaDesc.trim().toLength(1024)

										// Set name if it's specified as a header
										if(headers.contains("X-FILE-NAME"))
											mediaName = URLDecoder.decode(headers["X-FILE-NAME"], StandardCharsets.UTF_8.toString()).toLength(256)
										// Set description if it's specified as a header
										if(headers.contains("X-FILE-DESCRIPTION"))
											mediaName = URLDecoder.decode(headers["X-FILE-DESCRIPTION"], StandardCharsets.UTF_8.toString()).toLength(1024)
										// Set tags if they're specified as a header
										if(headers.contains("X-FILE-TAGS"))
											try {
												mediaTags = JsonArray(URLDecoder.decode("X-FILE-TAGS", StandardCharsets.UTF_8.toString()))
											} catch(e: Exception) { /* Failed to parse JSON array */
											}

										/* Create database entry */
										mediaModel.createMedia(
                                                id,
                                                mediaName,
                                                filename,
                                                mediaDesc.nullIfEmpty(),
                                                mediaTags,
                                                length,
                                                type,
                                                file,
                                                r.userId(),
                                                hash,
                                                thumbnail,
                                                meta
                                        )

										// Check if uploaded file is media
										if(!(headers.contains("X-NO-PROCESS") && headers["X-NO-PROCESS"].toLowerCase() == "true") && type.startsWith("video/") || type.startsWith("audio/")) {
											try {
												// Fetch processes created by the uploader for this type
												val processes = processesModel.fetchProcessesForMimeAndAccount(type, r.userId())

												// Queue processing jobs
												for(process in processes.rows.orEmpty()) {
													// Generate new media's ID
													val newId = generateString(10)

													// Queue job
													queueMediaProcessJobFromMedia(
                                                            sourceId = id,
                                                            newId = newId,
                                                            extension = process.getString("extension"),
                                                            creator = r.userId(),
                                                            settings = JsonObject(process.getString("settings"))
                                                    )
												}
											} catch(e: Exception) {
												logger.error("Failed to process uploaded media:")
												// Nothing that can be done
											}
										}
									}
								} catch(e: Exception) {
									logger.error("Failed to create media entry:")
									e.printStackTrace()
									upload = false
									error = "Database error"
									if(vertx().fileSystem().exists(saveLoc).await()) {
										logger.info("Deleting file $saveLoc")
										vertx().fileSystem().delete(saveLoc).await()
										logger.info("Deleted")
									}
								}

								// Send response if not already sent
								if(!r.response().ended()) {
									if(upload) {
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
}