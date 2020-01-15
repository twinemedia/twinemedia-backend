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

    // Returns all media, or searches media
    get("/api/v1/media", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.list")) {
                try {
                    // Collect parameters
                    val offset = (if (params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val sort = (if(params.contains("sort")) params["sort"].toInt() else 0).coerceIn(0, 5)
                    val mime = if(params.contains("mime")) params["mime"] else "*"
                    val query = if(params.contains("query")) params["query"] else ""
                    val searchItems = JsonObject()
                            .put("name", if(params.contains("searchNames")) params["searchNames"].toBoolean() else true)
                            .put("filename", if(params.contains("searchFilenames")) params["searchFilenames"].toBoolean() else true)
                            .put("tag", if(params.contains("searchTags")) params["searchTags"].toBoolean() else true)
                            .put("description", if(params.contains("searchDescriptions")) params["searchDescriptions"].toBoolean() else true)

                    // TODO Handle search

                    try {
                        // Fetch files
                        val media = fetchMediaList(offset, limit)

                        // Create JSON array of files
                        val arr = JsonArray()

                        for (file in media?.rows.orEmpty())
                            arr.add(file)

                        // Send files
                        r.success(JsonObject().put("media", arr))
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

    // Media tag search
    get("/api/v1/media/tags", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("file.list")) {
                if(params.contains("tags")) {
                    try {
                        val tags = JsonArray(params["tags"])
                        val offset = if(params.contains("offset")) params["offset"].toInt().coerceAtLeast(0) else 0
                        val limit = if(params.contains("limit")) params["limit"].toInt().coerceIn(0, 100) else 100

                        try {
                            // Fetch files
                            val filesRes = fetchMediaListByTags(tags, offset, limit)

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
}