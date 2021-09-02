package net.termer.twinemedia.controller

import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.Module.Companion.mediaSourceManager
import net.termer.twinemedia.db.dataobject.Source
import net.termer.twinemedia.db.scheduleTagsViewRefresh
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.model.ProcessesModel
import net.termer.twinemedia.model.SourcesModel
import net.termer.twinemedia.source.MediaSourceException
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.validation.TagsValidator
import net.termer.vertx.kotlin.validation.ParamValidator
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*


/**
 * Sets up all upload routes
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun uploadController() {
	for(hostname in appHostnames()) {
		// Allow headers
		router().options("/api/v1/media/upload").virtualHost(hostname).handler { r ->
			r.response()
					.corsAllowHeader("X-FILE-NAME")
					.corsAllowHeader("X-FILE-DESCRIPTION")
					.corsAllowHeader("X-FILE-TAGS")
					.corsAllowHeader("X-NO-THUMBNAIL")
					.corsAllowHeader("X-NO-PROCESS")
					.corsAllowHeader("X-MEDIA-SOURCE")

			r.next()
		}

		// Accepts media uploads
		// Permissions:
		//  - upload
		// Parameters:
		//  - Provide one file in multipart form data, called "file"
		// Headers:
		//  - X-FILE-NAME: (optional) URL encoded String, the name to give this file (decoded String must not be more than 256 characters long)
		//  - X-FILE-DESCRIPTION: (optional) URL encoded String, the description to give this file (decoded String must not be more than 1024 characters long)
		//  - X-FILE-TAGS: (optional) JSON array, tags to give to this file
		//  - X-NO-THUMBNAIL: (optional) Bool, whether to disable generating a thumbnail/preview for the uploaded file
		//  - X-NO-PROCESS: (optional) Bool, whether to disable running the uploader's process presets on the uploaded file
		//  - X-IGNORE-HASH: (optional) Bool, whether to force creating a new file on disk even if other files have the same hash
		//  - X-MEDIA-SOURCE: (optional) Integer, the ID of the source to upload this file to
		router().post("/api/v1/media/upload").virtualHost(hostname).handler { r ->
			r.request().pause()
			val headers = r.request().headers()
			GlobalScope.launch(vertx().dispatcher()) main@ {
				if(r.protectWithPermission("upload")) {
					val processesModel = ProcessesModel()
					val mediaModel = MediaModel()
					val sourcesModel = SourcesModel()

					// Check file size header
					if(!r.request().headers().contains("content-length")) {
						r.error("Missing content-length")
						return@main
					}
					val length = r.request().getHeader("content-length").toLong()

					// Figure out media source ID
					val sourceId = try {
						if(headers.contains("X-MEDIA-SOURCE"))
							headers["X-MEDIA-SOURCE"].toInt()
						else
							r.account().defaultSource
					} catch(e: NumberFormatException) {
						r.account().defaultSource
					}

					// Fetch media source and make sure it's accessible to the uploader
					val source: Source
					try {
						val sourceRes = sourcesModel.fetchSource(sourceId)

						// Make sure source exists
						if(sourceRes.rowCount() < 1) {
							r.error("Invalid source")
							return@main
						}

						source = sourceRes.first()
						val acc = r.account()

						// Make sure source is accessible to the account
						if(
								(!acc.hasPermission("sources.list") && source.id != acc.defaultSource) ||
								(source.creator != acc.id && !source.global && !acc.hasPermission("sources.list.all"))
						) {
							r.error("Invalid source")
							return@main
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch media source with ID $sourceId:")
						e.printStackTrace()
						return@main
					}

					// Get source instance
					val srcInst = mediaSourceManager.getSourceInstanceById(source.id)!!

					// Lock source until operation is complete
					val srcLock = srcInst.getLock().createLock()

					// Upload values
					var filename = "upload"
					var type = "unknown"
					var key: String? = null
					var usingExistingKey = false

					// Check if there is enough space left on the source
					val remainingStorage = try {
						srcInst.getRemainingStorage()
					} catch(e: MediaSourceException) {
						logger.error("Failed to check for remaining storage on media source ${source.id}:")
						e.printStackTrace()
						r.error("Internal error")
						return@main
					}
					if(remainingStorage != null && length > remainingStorage) {
						r.error("Not enough space remaining on media source ($remainingStorage bytes left, file is $length bytes)")
						return@main
					}

					// Generate ID
					val id = generateString(10)

					// File save location
					var saveLoc = ""

					// Only accept upload if not over the size limit
					if(length <= config.max_upload) {
						var upload = false
						var error = "No file sent"
						var alreadyGotUpload = false

						var endHandlerExecuting = false
						fun endHandler() {
							GlobalScope.launch(vertx().dispatcher()) {
								delay(100)

								if(!endHandlerExecuting) {
									endHandlerExecuting = true
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
											// Check if a file with the generated hash already exists in the target source
											val filesRes = mediaModel.fetchMediaByHashAndSource(hash, source.id)

											// Ignore check if X-IGNORE-HASH is true
											if(!(headers.contains("X-IGNORE-HASH") && headers["X-IGNORE-HASH"].toLowerCase() == "true") && filesRes.count() > 0) {
												usingExistingKey = true
												val fileRow = filesRes.first()

												// Get already uploaded file's key
												key = fileRow.key

												// Ignore thumbnail setting if X-NO-THUMBNAIL is true
												if(!(headers.contains("X-NO-THUMBNAIL") && headers["X-NO-THUMBNAIL"].toLowerCase() == "true")) {
													// Get already uploaded file's thumbnail
													thumbnail = fileRow.thumbnailFile
												}

												// Use existing file's metadata
												meta = fileRow.meta
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
															createVideoThumbnail(saveLoc, (probe.format.duration / 2).toInt(), "${config.thumbnails_location}$thumbId.jpg")
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
														createImagePreview(saveLoc, "${config.thumbnails_location}/$thumbId.jpg")
														thumbnail = "$thumbId.jpg"
													} catch(thumbEx: Exception) {
														// Failed to generate preview
													}
												}
											}

											/* Figure out file info */
											var mediaName = filenameToTitle(filename)
											var mediaDesc = ""
											var mediaTags = arrayOf<String>()

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
												mediaDesc = URLDecoder.decode(headers["X-FILE-DESCRIPTION"], StandardCharsets.UTF_8.toString()).toLength(1024)
											// Set tags if they're specified as a header
											if(headers.contains("X-FILE-TAGS"))
												try {
													val param = ParamValidator.Param("X-FILE-TAGS", URLDecoder.decode(headers["X-FILE-TAGS"], StandardCharsets.UTF_8.toString()), r)

													// Validate param
													val valRes = TagsValidator().validate(param)

													// Set tags if valid
													if(valRes.valid)
														mediaTags = (valRes.result as JsonArray).toStringArray()
												} catch(e: Exception) { /* Failed to parse JSON array */ }

											try {
												// Inspect file to get its actual size on disk
												val props = vertx().fileSystem().props(saveLoc).await()
												val fileSize = props.size()

												/* Send file to source if not using existing key */
												if(!usingExistingKey)
													srcInst.uploadFile(saveLoc, key!!)

												/* Create database entry */
												mediaModel.createMedia(
														id,
														mediaName,
														filename,
														mediaDesc.nullIfEmpty(),
														mediaTags,
														fileSize,
														type,
														key!!,
														r.userId(),
														hash,
														thumbnail,
														meta,
														source.id
												)

												// Refresh tags
												if(mediaTags.isNotEmpty())
													scheduleTagsViewRefresh()

												// Check if uploaded file is media
												if(!(headers.contains("X-NO-PROCESS") && headers["X-NO-PROCESS"].toLowerCase() == "true") && (type.startsWith("video/") || type.startsWith("audio/"))) {
													try {
														// Fetch processes created by the uploader for this type
														val processes = processesModel.fetchProcessesForMimeAndAccount(type, r.userId())

														// If there are no processes, go ahead and delete the uploaded file
														// Otherwise, make it available to the media processor to avoid having to download it from the source
														if(processes.rowCount() > 0) {
															putMediaCachePath(id, saveLoc)

															// Queue processing jobs
															for(process in processes) {
																// Queue job
																queueMediaProcessJob(
																		mediaId = id,
																		newId = null,
																		extension = process.extension,
																		creator = r.userId(),
																		settings = process.settings
																)
															}
														} else if(vertx().fileSystem().exists(saveLoc).await()) {
															vertx().fileSystem().delete(saveLoc).await()
														}
													} catch(e: Exception) {
														logger.error("Failed to process uploaded media:")
														// Nothing that can be done
													}
												}
											} catch(e: MediaSourceException) {
												logger.error("Failed to upload file to media source:")
												e.printStackTrace()
												upload = false
												error = "Media source error"
												if(vertx().fileSystem().exists(saveLoc).await()) {
													logger.info("Deleting file $saveLoc")
													vertx().fileSystem().delete(saveLoc).await()
													logger.info("Deleted")
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

									// Remove source lock
									srcInst.getLock().deleteLock(srcLock)

									// Send response if not already sent
									if(!r.response().ended()) {
										if(upload) {
											r.success(json {obj(
													"id" to id
											)})
										} else {
											r.error(error)
										}
									}
								}
							}
						}

						// Prepare for file uploads
						r.request().isExpectMultipart = true
						r.request().uploadHandler { upl ->
							// Make sure only one upload is handled
							if(!alreadyGotUpload) {
								alreadyGotUpload = true
								upload = true

								GlobalScope.launch(vertx().dispatcher()) {
									// Collect info
									filename = upl.filename()
									type = upl.contentType().ifBlank { mimeFor(upl.filename()) }
									key = srcInst.filenameToKey(filename)
									val tmpSaveLoc = config.upload_location+key

									fun errHdlr(e: Throwable) {
										logger.error("Failed to handle upload:")
										e.printStackTrace()
										upload = false
										error = "Internal error"

										GlobalScope.launch(vertx().dispatcher()) {
											logger.info("Deleting file $saveLoc")
											vertx().fileSystem().delete(saveLoc).await()
											logger.info("Deleted")
										}
									}

									// Handle upload errors
									upl.exceptionHandler(::errHdlr)

									try {
										// Stream upload to file
										upl.streamToFileSystem(tmpSaveLoc).await()

										saveLoc = tmpSaveLoc
										endHandler()
									} catch(e: Throwable) {
										errHdlr(e)
									}
								}
							}
						}

						r.request().endHandler {
							GlobalScope.launch(vertx().dispatcher()) {
								// Check if the file even got to the upload stage
								if(!upload && saveLoc == "") {
									r.error("Empty file given")
									return@launch
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