package net.termer.twinemedia.controller

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.util.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Setups up all routes for creating child media files
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun mediaChildController() {
	for(hostname in appHostnames()) {
		// Creates a new child media entry and starts processing the new version
		// Permissions:
		//  - files.child
		// Route parameters:
		//  - id: String, the alphanumeric ID of the source media file
		// Parameters:
		//  - extension: String, the file extension of the new child file
		//  - frame_rate: (optional) Integer, the frame rate of the child file (will be ignored if source is not a video)
		//  - audio_bitrate (optional): Integer, the audio bitrate of the child file
		//  - video_bitrate (optional): Integer, the video bitrate of the child file (will be ignored if source is not a video)
		//  - width (optional): Integer, the width of the child file (will be ignored if source is not a video, and if left out, aspect ratio will be kept)
		//  - height (optional): Integer, the height of the child file (will be ignored if source is not a video, and if left out, aspect ratio will be kept)
		router().post("/api/v1/media/:id/child").virtualHost(hostname).handler { r ->
			val id = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.child")) {
					val mediaModel = MediaModel(r.account())

					try {
						val mediaRes = mediaModel.fetchMedia(id)

						// Check if media exists
						if(mediaRes.count() > 0) {
							val media = mediaRes.first()

							// Check if media was created by the user
							if(media.creator != r.userId() && !r.hasPermission("files.child.all")) {
								r.unauthorized()
								return@launch
							}

							// Check if media is a child
							if(media.parent != null) {
								r.error("Cannot create child of child")
								return@launch
							}

							// Check if source is a media file
							if(!media.mime.startsWith("video/") && !media.mime.startsWith("audio/")) {
								r.error("Source media is not audio or video")
								return@launch
							}

							// Request validation
							val v = RequestValidator()
									.param("extension", StringValidator()
											.toLowerCase()
											.maxLength(200)
											.minLength(1)
											.isInArray(if(media.mime.startsWith("video/"))
												videoExtensions
											else
												audioExtensions))
									.optionalParam("frame_rate", IntValidator()
											.min(-1)
											.max(512), -1)
									.optionalParam("audio_bitrate", IntValidator()
											.min(-1)
											.max(51200), -1)
									.optionalParam("video_bitrate", IntValidator()
											.min(-1)
											.max(51200), -1)
									.optionalParam("width", IntValidator()
											.min(-1)
											.max(7680), -1)
									.optionalParam("height", IntValidator()
											.min(-1)
											.max(4320), -1)

							if(v.validate(r)) {
								val extension = v.parsedParam("extension") as String

								// Put settings
								val settings = JsonObject()
										.put("audio_bitrate", (v.parsedParam("audio_bitrate") as Int).toLong())

								// Put video-specific settings
								if(media.mime.startsWith("video/")) {
									// Set dimensions
									settings.put("width", (v.parsedParam("width") as Int).toLong())
									settings.put("height", (v.parsedParam("height") as Int).toLong())

									// Set framerate
									settings.put("frame_rate", (v.parsedParam("frame_rate") as Int).toLong())

									// Set bitrate
									settings.put("video_bitrate", (v.parsedParam("video_bitrate") as Int).toLong())
								}

								try {
									// Generate new media's ID
									val newId = generateString(10)

									try {
										// Generate output key
										var outKey = ((if(media.key.contains("."))
											media.key.substring(0, media.key.lastIndexOf('.'))
										else
											media.key)+"-child-${System.currentTimeMillis()/1000L}.$extension").replace(' ', '_')
										if(outKey.length > 256)
											outKey = outKey.substring(outKey.length-256)

										// Guess mime
										val outMime = mimeFor(extension)

										// Generate new filename
										val filename = media.filename
										val dotIdx = filename.lastIndexOf('.')
										val outFilename = (if(dotIdx < 0)
											filename
										else
											filename.substring(0, dotIdx))+".$extension"

										// Create media entry
										mediaModel.createMedia(
												id = newId,
												name = media.name,
												filename = outFilename,
												size = -1,
												mime = outMime,
												key = outKey,
												creator = r.userId(),
												hash = "PROCESSING",
												thumbnailFile = null,
												meta = JsonObject(),
												parent = media.internalId,
												processing = true,
												sourceId = media.source
										)

										// Create new processing job
										queueMediaProcessJob(
												mediaId = id,
												newId = newId,
												extension = extension,
												creator = r.userId(),
												settings = settings
										)

										// Send success
										r.success(json {obj(
												"id" to newId
										)})
									} catch(e: Exception) {
										logger.error("Failed to queue new media processing job:")
										e.printStackTrace()
										r.error("Database error")
									}
								} catch(e: Exception) {
									logger.error("Failed to create media entry:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								r.error(v)
							}
						} else {
							r.error("File does not exist")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch media file:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}
	}
}