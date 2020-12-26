package net.termer.twinemedia.controller

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.util.*

/**
 * Setups up all routes for creating child media files
 */
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
			val params = r.request().params()
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.child")) {
					val mediaModel = MediaModel(r.account())

					try {
						val mediaRes = mediaModel.fetchMedia(id)

						// Check if media exists
						if(mediaRes != null && mediaRes.rows.size > 0) {
							val media = mediaRes.rows[0]

							// Check if media was created by the user
							if(media.getInteger("media_creator") != r.userId() && !r.hasPermission("files.child.all")) {
								r.unauthorized()
								return@launch
							}

							// Check if media is a child
							if(media.getInteger("media_parent") != null) {
								r.error("Cannot create child of child")
								return@launch
							}

							// Check if minimum params are met
							if(params.contains("extension")) {
								try {
									val extension = params["extension"]
									val settings = JsonObject()

									// Check if source is a media file
									if(!media.getString("media_mime").startsWith("video/") && !media.getString("media_mime").startsWith("audio/")) {
										r.error("Source media is not audio or video")
										return@launch
									}

									// Check if extension is valid
									if(
											!(media.getString("media_mime").startsWith("video/") && videoExtensions.contains(extension))
											&&
											!(media.getString("media_mime").startsWith("audio/") && audioExtensions.contains(extension))
									) {
										r.error("Invalid extension $extension for media process")
										return@launch
									}

									// Check if audio bitrate is specified
									if(params.contains("audio_bitrate"))
										settings.put("audio_bitrate", params["audio_bitrate"].toInt())
									else
										settings.put("audio_bitrate", 0L)


									// Check if file is a video or audio, or reject
									if(media.getString("media_mime").startsWith("video/")) {
										// Set dimensions
										if(params.contains("width"))
											settings.put("width", params["width"].toInt())
										else
											settings.put("width", -1)

										if(params.contains("height"))
											settings.put("height", params["height"].toInt())
										else
											settings.put("height", -1)

										// Set framerate
										if(params.contains("frame_rate"))
											settings.put("frame_rate", params["frame_rate"].toInt())
										else
											settings.put("frame_rate", -1)

										// Set bitrate
										if(params.contains("video_bitrate"))
											settings.put("video_bitrate", params["video_bitrate"].toInt())
										else
											settings.put("video_bitrate", 0L)

									} else if(!media.getString("media_mime").startsWith("audio/")) {
										r.error("File must be audio or video")
										return@launch
									}

									try {
										// Generate new media's ID
										val newId = generateString(10)

										try {
											// Create new processing job
											queueMediaProcessJobFromMedia(
													sourceId = id,
													newId = newId,
													extension = extension,
													creator = r.userId(),
													settings = settings
											)

											// Send success
											r.success(json {
												obj("id" to newId)
											})
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
								} catch(e: Exception) {
									r.error("Invalid parameters")
								}
							} else {
								r.error("Must include extension")
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