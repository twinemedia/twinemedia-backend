package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.validation.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Sets up all routes for editing and modifying processing presets
 * @since 1.0.0
 */
fun processesController() {
	for(hostname in appHostnames()) {
		// Returns info about a process
		// Permissions:
		//  - processes.view
		// Route parameters:
		//  - id: Integer, the ID of the process to view
		router().get("/api/v1/process/:id").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("processes.view")) {
					val processesModel = ProcessesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())

					if(v.validate(r)) {
						val id = v.parsedRouteParam("id") as Int

						try {
							val processRes = processesModel.fetchProcessInfo(id)

							// Check if process exists
							if(processRes.count() > 0) {
								// Send
								r.success(processRes.iterator().next().toJson())
							} else {
								r.error("Process does not exist")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch process:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Deletes a process
		// Permissions:
		//  - processes.delete
		// Route parameters:
		//  - id: Integer, the ID of the process to delete
		router().post("/api/v1/process/:id/delete").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("processes.delete")) {
					val processesModel = ProcessesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())

					if(v.validate(r)) {
						val id = v.parsedRouteParam("id") as Int

						try {
							// Check if process exists
							val processRes = processesModel.fetchProcess(id)
							if(processRes.count() > 0) {
								val process = processRes.iterator().next()

								// Check if process preset was created by the user
								if(process.creator != r.userId() && !r.hasPermission("processes.delete.all")) {
									r.unauthorized()
									return@launch
								}

								try {
									// Delete process
									processesModel.deleteProcess(id)

									// Success
									r.success()
								} catch(e: Exception) {
									logger.error("Failed to delete process:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								r.error("Process does not exist")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch process:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Edits an existing process entry
		// Permissions:
		//  - processes.edit
		// Parameters:
		//  - mime: String, the mime for this process, can use % as a wildcard character
		//  - extension: String, the file extension for this process
		//  - frame_rate: (optional) Integer, the frame rate setting for this process (will be ignored if mime is not a video)
		//  - audio_bitrate (optional): Integer, the audio bitrate setting for this process
		//  - video_bitrate (optional): Integer, the video bitrate setting for this process (will be ignored if mime is not a video)
		//  - width (optional): Integer, the width setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
		//  - height (optional): Integer, the height setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
		// Route parameters:
		//  - id: Integer, the ID of the process to edit
		router().post("/api/v1/process/:id/edit").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("processes.create")) {
					val processesModel = ProcessesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())
							.param("mime", Presets.mimeValidator(true))
							.param("extension", StringValidator()
									.toLowerCase()
									.maxLength(200)
									.minLength(1)
									.isInArray(if(r.request().params()["mime"].toLowerCase().startsWith("video/"))
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
						val id = r.pathParam("id").toInt()

						val mime = v.parsedParam("mime") as String

						// Put settings
						val settings = JsonObject()
								.put("extension", v.parsedParam("extension") as String)
								.put("audio_bitrate", (v.parsedParam("audio_bitrate") as Int).toLong())

						// Put video-specific settings
						if(mime.startsWith("video/")) {
							// Set dimensions
							settings.put("width", (v.parsedParam("width") as Int).toLong())
							settings.put("height", (v.parsedParam("height") as Int).toLong())

							// Set framerate
							settings.put("frame_rate", (v.parsedParam("frame_rate") as Int).toLong())

							// Set bitrate
							settings.put("video_bitrate", v.parsedParam("video_bitrate"))
						}

						try {
							// Check if process exists
							val processRes = processesModel.fetchProcess(id)
							if(processRes.count() > 0) {
								val process = processRes.iterator().next()

								// Check if process preset was created by the user
								if(process.creator != r.userId() && !r.hasPermission("processes.edit.all")) {
									r.unauthorized()
									return@launch
								}

								try {
									// Update entry
									processesModel.updateProcess(id, mime, settings)

									// Send success
									r.success()
								} catch(e: Exception) {
									logger.error("Failed to create new process entry:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								r.error("Process does not exist")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch process:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Returns all processes
		// Permissions:
		//  - processes.list
		// Parameters:
		//  - offset: (optional) Integer at least 0 that sets the offset of returned results
		//  - limit: (optional) Integer from 0 to 100, sets the amount of results to return
		//  - order: (optional) Integer from 0 to 7, denotes the type of sorting to use (date newest to oldest, date oldest to newest, MIME ascending, MIME descending, creator ID highest to lowest, creator ID lowest to highest, modified date newest to oldest, modified date oldest to newest)
		router().get("/api/v1/processes").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("processes.list")) {
					val processesModel = ProcessesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.optionalParam("offset", Presets.resultOffsetValidator(), 0)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("order", IntValidator()
									.min(0)
									.max(7), 0)

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int

						try {
							val processes = JsonArray()

							// Fetch processes
							val processesRes = processesModel.fetchProcesses(offset, limit, order)

							// Add processes
							for(process in processesRes)
								processes.add(process.toJson())

							// Send
							r.success(json {
                                obj("processes" to processes)
                            })
						} catch(e: Exception) {
							logger.error("Failed to fetch process:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Creates a new process entry
		// Permissions:
		//  - processes.create
		// Parameters:
		//  - mime: String, the mime for this process
		//  - extension: String, the file extension for this process
		//  - frame_rate: (optional) Integer, the frame rate setting for this process (will be ignored if mime is not a video)
		//  - audio_bitrate (optional): Integer, the audio bitrate setting for this process
		//  - video_bitrate (optional): Integer, the video bitrate setting for this process (will be ignored if mime is not a video)
		//  - width (optional): Integer, the width setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
		//  - height (optional): Integer, the height setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
		router().post("/api/v1/process/create").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("processes.create")) {
					val processesModel = ProcessesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.param("mime", StringValidator()
									.toLowerCase()
									.regex(Regex("^(video|audio)/[%\\-\\w.]+$")))
							.param("extension", StringValidator()
									.toLowerCase()
									.maxLength(200)
									.minLength(1)
									.isInArray(if(r.request().params()["mime"].toLowerCase().startsWith("video/"))
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
						val mime = v.parsedParam("mime") as String

						// Put settings
						val settings = JsonObject()
								.put("extension", v.parsedParam("extension") as String)
								.put("audio_bitrate", (v.parsedParam("audio_bitrate") as Int).toLong())

						// Put video-specific settings
						if(mime.startsWith("video/")) {
							// Set dimensions
							settings.put("width", (v.parsedParam("width") as Int).toLong())
							settings.put("height", (v.parsedParam("height") as Int).toLong())

							// Set framerate
							settings.put("frame_rate", (v.parsedParam("frame_rate") as Int).toLong())

							// Set bitrate
							settings.put("video_bitrate", (v.parsedParam("video_bitrate") as Int).toLong())
						}

						try {
							// Create new entry
							processesModel.createProcess(mime, settings, r.userId())

							// Send success
							r.success()
						} catch(e: Exception) {
							logger.error("Failed to create new process entry:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}
	}
}