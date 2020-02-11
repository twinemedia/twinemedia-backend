package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.file.deleteAwait
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.Twine
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.protectWithPermission
import net.termer.twinemedia.util.success
import java.lang.NumberFormatException

/**
 * Sets up all routes for retrieving and modifying file info + processing files
 * @since 1.0
 */
fun mediaController() {
    val domain = Twine.domains().byName(config.domain).domain()

    // Returns all media files
    // Permissions:
    //  - files.list
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - mime: String, the mime pattern to search for, can use % as a wildcard character
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large)
    get("/api/v1/media", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.list")) {
                try {
                    // Collect parameters
                    val offset = (if (params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val mime = if(params.contains("mime")) params["mime"] else "%"
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)

                    try {
                        // Fetch files
                        val media = fetchMediaList(offset, limit, mime, order)

                        // Create JSON array of files
                        val arr = JsonArray()

                        for (file in media?.rows.orEmpty())
                            arr.add(file)

                        // Send files
                        r.success(JsonObject().put("files", arr))
                    } catch(e : Exception) {
                        logger.error("Failed to create fetch files:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Searches media files using a plaintext query
    // Permissions:
    //  - files.list
    // Parameters:
    //  - query: String, the plaintext query to search
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - mime: String, the mime pattern to search for, can use % as a wildcard character
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large)
    //  - searchNames: Bool, whether to search file names
    //  - searchFilenames: Bool, whether to search file filenames
    //  - searchTags: Bool, whether to search file tags
    //  - searchDescriptions: Bool, whether to search file descriptions
    get("/api/v1/media/search", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.list")) {
                try {
                    // Collect parameters
                    val offset = (if (params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val mime = if(params.contains("mime")) params["mime"] else "%"
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)
                    val query = if(params.contains("query")) params["query"] else ""
                    val searchItems = JsonObject()
                            .put("name", if(params.contains("searchNames")) params["searchNames"].toBoolean() else true)
                            .put("filename", if(params.contains("searchFilenames")) params["searchFilenames"].toBoolean() else true)
                            .put("tag", if(params.contains("searchTags")) params["searchTags"].toBoolean() else true)
                            .put("description", if(params.contains("searchDescriptions")) params["searchDescriptions"].toBoolean() else true)

                    try {
                        // Fetch files
                        val media = when(query.isNotEmpty()) {
                            true -> {
                                fetchMediaByPlaintextQuery(query, offset, limit, order, mime,
                                        searchItems.getBoolean("name"),
                                        searchItems.getBoolean("filename"),
                                        searchItems.getBoolean("tag"),
                                        searchItems.getBoolean("description")
                                )
                            }
                            else -> fetchMediaList(offset, limit, mime, order)
                        }

                        // Create JSON array of files
                        val arr = JsonArray()

                        for (file in media?.rows.orEmpty())
                            arr.add(file)

                        // Send files
                        r.success(JsonObject().put("files", arr))
                    } catch(e : Exception) {
                        logger.error("Failed to create fetch files:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Media file tag search
    // Permissions:
    //  - files.list
    // Parameters:
    //  - tags: JSON array, the tags to search for
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - mime: String, the mime pattern to search for, can use % as a wildcard character
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large)
    get("/api/v1/media/tags", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("file.list")) {
                if(params.contains("tags")) {
                    try {
                        val tags = JsonArray(params["tags"])
                        val offset = if(params.contains("offset")) params["offset"].toInt().coerceAtLeast(0) else 0
                        val limit = if(params.contains("limit")) params["limit"].toInt().coerceIn(0, 100) else 100
                        val order = if(params.contains("order")) params["order"].toInt() else 0
                        val mime = if(params.contains("mime")) params["mime"] else "%"

                        try {
                            // Fetch files
                            val filesRes = fetchMediaListByTags(tags, mime, order, offset, limit)

                            // Compose response
                            val files = JsonArray()
                            for(row in filesRes?.rows.orEmpty())
                                files.add(row)

                            r.success(JsonObject().put("files", files))
                        } catch(e : Exception) {
                            logger.error("Failed to fetch file list by tags:")
                            e.printStackTrace()
                            r.error("Database error")
                        }
                    } catch(e : Exception) {
                        // Tags are not a JSON array
                        r.error("Tags must be a JSON array")
                    }
                } else {
                    r.error("Must provide tags as JSON array to search")
                }
            }
        }
    }

    // Returns info about the specified media file
    // Permissions:
    //  - files.view
    // Route parameters:
    //  - file: String, the alphanumeric ID of the requested media file
    get("/api/v1/media/:file", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.view")) {
                val fileId = r.pathParam("file")

                try {
                    // Fetch media file
                    val mediaRes = fetchMediaInfo(fileId)

                    // Check if it exists
                    if (mediaRes != null && mediaRes.rows.size > 0) {
                        // Fetch media info
                        val media = mediaRes.rows[0]

                        // Fetch and remove internal values
                        val id = media.getInteger("internal_id")
                        media.remove("internal_id")
                        val parent = media.getInteger("internal_parent")
                        media.remove("internal_parent")

                        if(parent != null) {
                            // Fetch parent
                            val parentRes = fetchMediaInfo(parent)
                            if (parentRes != null && parentRes.rows.size > 0) {
                                media.put("parent", parentRes.rows[0])
                                media.getJsonObject("parent").remove("internal_id")
                                media.getJsonObject("parent").remove("internal_parent")
                            } else {
                                media.put("parent", null as String?)
                            }
                        }

                        // Fetch children
                        val childrenRes = fetchMediaChildren(id)

                        // If there wasn't an error in fetching, this will never be null
                        if(childrenRes != null) {
                            val children = JsonArray()

                            // Add child files to JSON
                            for(child in childrenRes.rows)
                                children.add(child)
                            media.put("children", children)

                            // Convert tags to real JSON array
                            media.put("tags", JsonArray(media.getString("tags")))

                            // Return response
                            r.success(media)
                        } else {
                            r.error("Internal error")
                        }
                    } else {
                        r.error("File does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch file info:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Edits a media file's metadata
    // Permissions:
    //  - files.edit
    // Route parameters:
    //  - file: String, the alphanumeric ID of the requested media file
    // Parameters:
    //  - name (optional): String, the new name of the media file, can be null
    //  - desc (optional): String, the new description of the media file, can be null
    //  - tags (optional): Json array, the new tags to give this media file
    post("/api/v1/media/:file/edit", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.edit")) {
                val fileId = r.pathParam("file")

                try {
                    // Fetch media file
                    val mediaRes = fetchMediaInfo(fileId)

                    // Check if it exists
                    if (mediaRes != null && mediaRes.rows.size > 0) {
                        // Fetch media info
                        val media = mediaRes.rows[0]

                        try {
                            // Resolve edit values
                            val name : String? = if (params["name"] != null) params["name"] else media.getString("name")
                            val desc : String? = if (params["description"] != null) params["description"] else media.getString("description")
                            val tags = if (params["tags"] != null) JsonArray(params["tags"]) else JsonArray(media.getString("tags"))

                            try {
                                updateMediaInfo(media.getInteger("internal_id"), name, desc, tags)

                                r.success()
                            } catch(e : Exception) {
                                logger.error("Failed to edit file info:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } catch(e : Exception) {
                            // Invalid tags JSON array
                            r.error("Tags must be a JSON array")
                        }
                    } else {
                        r.error("File does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch file:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Deletes a media file
    // Permissions:
    //  - files.delete
    // Route parameters:
    //  - file: String, the alphanumeric ID of the requested media file
    post("/api/v1/media/:file/delete", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.delete")) {
                val fileId = r.pathParam("file")

                try {
                    val mediaRes = fetchMedia(fileId)

                    if(mediaRes != null && mediaRes.rows.size > 0) {
                        val media = mediaRes.rows[0]

                        // Check if files with the same hash exist
                        val hashMediaRes = fetchMediaByHash(media.getString("media_file_hash"))

                        if(hashMediaRes != null && hashMediaRes.rows.size < 2) {
                            // Delete media files
                            val fs = vertx().fileSystem()
                            try {
                                // Delete main file
                                val file = media.getString("media_file")

                                fs.deleteAwait(config.upload_location + file)
                            } catch (e: Exception) {
                                // Failed to delete main file
                                logger.error("Failed to delete file ${media.getString("media_file")}:")
                                e.printStackTrace()
                                r.error("Internal error")
                                return@launch
                            }
                            if (media.getBoolean("media_thumbnail")) {
                                try {
                                    // Delete thumbnail file
                                    val file = media.getString("media_thumbnail_file")

                                    fs.deleteAwait(config.upload_location + "thumbnails/" + file)
                                } catch (e: Exception) {
                                    // Failed to delete thumbnail file
                                    logger.error("Failed to delete file ${media.getString("media_thumbnail_file")}")
                                    e.printStackTrace()
                                    r.error("Internal error")
                                    return@launch
                                }
                            }
                        }

                        try {
                            // Delete database entry
                            deleteMedia(fileId)

                            r.success()
                        } catch(e : Exception) {
                            logger.error("Failed to delete file entry for ID $fileId:")
                            e.printStackTrace()
                            r.error("Internal error")
                        }
                    } else {
                        r.error("File does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to delete file:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }
}