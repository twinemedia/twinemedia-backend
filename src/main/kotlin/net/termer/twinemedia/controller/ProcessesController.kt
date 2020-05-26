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

/**
 * Sets up all routes for editing and modifying processing presets
 * @since 1.0
 */
fun processesController() {
    val domain = appDomain()

    // Returns info about a process
    // Permissions:
    //  - processes.view
    // Route parameters:
    //  - id: Integer, the ID of the process to view
    get("/api/v1/process/:id", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("processes.view")) {
                try {
                    val id = r.pathParam("id").toInt()

                    try {
                        val processRes = fetchProcessInfo(id)

                        // Check if process exists
                        if(processRes?.rows != null && processRes.rows.size > 0) {
                            // Send
                            processRes.rows[0].put("settings", JsonObject(processRes.rows[0].getString("settings")))
                            r.success(processRes.rows[0])
                        } else {
                            r.error("Process does not exist")
                        }
                    } catch(e : Exception) {
                        logger.error("Failed to fetch process:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Deletes a process
    // Permissions:
    //  - processes.delete
    // Route parameters:
    //  - id: Integer, the ID of the process to delete
    post("/api/v1/process/:id/delete", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("processes.delete")) {
                try {
                    val id = r.pathParam("id").toInt()

                    try {
                        // Check if process exists
                        val processRes = fetchProcess(id)
                        if (processRes?.rows != null && processRes.rows.size > 0) {
                            try {
                                // Delete process
                                deleteProcess(id)

                                // Success
                                r.success()
                            } catch(e : Exception) {
                                logger.error("Failed to delete process:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("Process does not exist")
                        }
                    } catch(e : Exception) {
                        logger.error("Failed to fetch process:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Edits an existing process entry
    // Permissions:
    //  - processes.edit
    // Parameters:
    //  - mime: String, the mime for this process
    //  - frame_rate: (optional) Integer, the frame rate setting for this process (will be ignored if mime is not a video)
    //  - audio_bitrate (optional): Integer, the audio bitrate setting for this process
    //  - video_bitrate (optional): Integer, the video bitrate setting for this process (will be ignored if mime is not a video)
    //  - width (optional): Integer, the width setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
    //  - height (optional): Integer, the height setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
    post("/api/v1/process/:id/edit", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            val params = r.request().params()

            if(r.protectWithPermission("processes.create")) {
                try {
                    val id = r.pathParam("id").toInt()

                    // Ensure mime is specified
                    if(params.contains("mime") && params.contains("extension")) {
                        val mime = params["mime"]
                        val settings = JsonObject()
                                .put("extension", params["extension"])

                        // Check if audio bitrate is specified
                        if(params.contains("audio_bitrate"))
                            settings.put("audio_bitrate", params["audio_bitrate"].toInt())
                        else
                            settings.put("audio_bitrate", 0L)

                        if(mime.startsWith("video/")) {
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
                        } else {
                            r.error("Mime must be either video or audio")
                            return@launch
                        }

                        try {
                            // Check if process exists
                            val processRes = fetchProcess(id)
                            if (processRes?.rows != null && processRes.rows.size > 0) {
                                try {
                                    // Update entry
                                    updateProcess(id, mime, settings)

                                    // Send success
                                    r.success()
                                } catch (e: Exception) {
                                    logger.error("Failed to create new process entry:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
                            } else {
                                r.error("Process does not exist")
                            }
                        } catch(e : Exception) {
                            logger.error("Failed to fetch process:")
                            e.printStackTrace()
                            r.error("Database error")
                        }
                    } else {
                        r.error("Must specify mime and extension")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Returns all processes
    // Permissions:
    //  - processes.list
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    get("/api/v1/processes", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("processes.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)

                    try {
                        val processes = JsonArray()

                        // Fetch processes
                        val processesRes = fetchProcesses(offset, limit)

                        // Add processes
                        for (process in processesRes?.rows.orEmpty())
                            processes.add(
                                    process
                                            .put("settings", JsonObject(process.getString("settings")))
                            )

                        // Send
                        r.success(json {
                            obj("processes" to processes)
                        })
                    } catch (e: Exception) {
                        logger.error("Failed to fetch process:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Creates a new process entry
    // Permissions:
    //  - processes.create
    // Parameters:
    //  - mime: String, the mime for this process
    //  - frame_rate: (optional) Integer, the frame rate setting for this process (will be ignored if mime is not a video)
    //  - audio_bitrate (optional): Integer, the audio bitrate setting for this process
    //  - video_bitrate (optional): Integer, the video bitrate setting for this process (will be ignored if mime is not a video)
    //  - width (optional): Integer, the width setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
    //  - height (optional): Integer, the height setting for this process (will be ignored if mime is not a video, and if left out, aspect ratio will be kept)
    post("/api/v1/process/create", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            val params = r.request().params()

            if(r.protectWithPermission("processes.create")) {
                try {
                    // Ensure mime is specified
                    if(params.contains("mime") && params.contains("extension")) {
                        val mime = params["mime"]
                        val settings = JsonObject()
                                .put("extension", params["extension"])

                        // Check if audio bitrate is specified
                        if(params.contains("audio_bitrate"))
                            settings.put("audio_bitrate", params["audio_bitrate"].toInt())
                        else
                            settings.put("audio_bitrate", 0)

                        if(mime.startsWith("video/")) {
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
                                settings.put("video_bitrate", 0)
                        } else {
                            r.error("Mime must be either video or audio")
                            return@launch
                        }

                        try {
                            // Create new entry
                            createProcess(mime, settings, r.userId())

                            // Send success
                            r.success()
                        } catch(e : Exception) {
                            logger.error("Failed to create new process entry:")
                            e.printStackTrace()
                            r.error("Database error")
                        }
                    } else {
                        r.error("Must specify mime and extension")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }
}