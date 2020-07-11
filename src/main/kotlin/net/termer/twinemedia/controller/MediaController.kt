package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.file.deleteAwait
import io.vertx.kotlin.core.file.existsAwait
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*

/**
 * Sets up all routes for retrieving and modifying file info + processing files
 * @since 1.0
 */
fun mediaController() {
    val domain = appDomain()

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
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val mime = if(params.contains("mime")) params["mime"] else "%"
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)

                    try {
                        // Fetch files
                        val media = fetchMediaList(offset, limit, mime, order)

                        // Create JSON array of files
                        val arr = JsonArray()

                        for(file in media?.rows.orEmpty())
                            arr.add(file)

                        // Send files
                        r.success(JsonObject().put("files", arr))
                    } catch(e : Exception) {
                        logger.error("Failed to fetch files:")
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
    //  - searchNames (optional): Bool, whether to search file names (if not specified defaults to true)
    //  - searchFilenames (optional): Bool, whether to search file filenames (if not specified defaults to true)
    //  - searchTags (optional): Bool, whether to search file tags (if not specified defaults to true)
    //  - searchDescriptions (optional): Bool, whether to search file descriptions (if not specified defaults to true)
    get("/api/v1/media/search", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val mime = if(params.contains("mime")) params["mime"] else "%"
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)
                    val query = if(params.contains("query")) params["query"] else ""
                    val searchItems = JsonObject()
                            .put("name", if(params.contains("searchNames")) params["searchNames"]!!.toBoolean() else true)
                            .put("filename", if(params.contains("searchFilenames")) params["searchFilenames"]!!.toBoolean() else true)
                            .put("tag", if(params.contains("searchTags")) params["searchTags"]!!.toBoolean() else true)
                            .put("description", if(params.contains("searchDescriptions")) params["searchDescriptions"]!!.toBoolean() else true)

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

                        for(file in media?.rows.orEmpty())
                            arr.add(file)

                        // Send files
                        r.success(JsonObject().put("files", arr))
                    } catch(e : Exception) {
                        logger.error("Failed to fetch files:")
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
    //  - excludeTags (optional): JSON array, the tags to exclude when searching
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - mime: String, the mime pattern to search for, can use % as a wildcard character
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large)
    get("/api/v1/media/tags", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.list")) {
                if(params.contains("tags")) {
                    try {
                        val tags = JsonArray(params["tags"])
                        val excludeTags = if(params.contains("excludeTags")) JsonArray(params["excludeTags"]) else null
                        val offset = if(params.contains("offset")) params["offset"].toInt().coerceAtLeast(0) else 0
                        val limit = if(params.contains("limit")) params["limit"].toInt().coerceIn(0, 100) else 100
                        val order = if(params.contains("order")) params["order"].toInt() else 0
                        val mime = if(params.contains("mime")) params["mime"] else "%"

                        try {
                            // Fetch files
                            val filesRes = fetchMediaListByTags(tags, excludeTags, mime, order, offset, limit)

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
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.view")) {
                val fileId = r.pathParam("file")

                try {
                    // Fetch media file
                    val mediaRes = fetchMediaInfo(fileId)

                    // Check if it exists
                    if(mediaRes != null && mediaRes.rows.size > 0) {
                        // Fetch media info
                        val media = mediaRes.rows[0]

                        // Fetch and remove internal values
                        val id = media.getInteger("internal_id")
                        media.remove("internal_id")
                        val parent = media.getInteger("internal_parent")
                        media.remove("internal_parent")

                        // JSON-ify media metadata
                        media.put("meta", JsonObject(media.getString("meta")))

                        // Put null parent object (will be replaced if a parent exists)
                        media.put("parent", null as JsonObject?)

                        // JSON array of children
                        val children = JsonArray()

                        if(parent == null) {
                            try {
                                // Fetch children
                                val childrenRes = fetchMediaChildrenInfo(id)

                                // If there wasn't an error in fetching, this will never be null
                                if (childrenRes != null) {
                                    // Add child files to JSON
                                    for (child in childrenRes.rows)
                                        children.add(child)
                                } else {
                                    r.error("Internal error")
                                }
                            } catch(e : Exception) {
                                logger.error("Failed to fetch children for file:")
                                e.printStackTrace()
                                r.error("Database error")
                                return@launch
                            }
                        } else {
                            try {
                                // Fetch parent
                                val parentRes = fetchMediaInfo(parent)
                                if (parentRes != null && parentRes.rows.size > 0) {
                                    media.put("parent", parentRes.rows[0])
                                    media.getJsonObject("parent").remove("internal_id")
                                    media.getJsonObject("parent").remove("internal_parent")
                                } else {
                                    media.put("parent", null as String?)
                                }
                            } catch(e : Exception) {
                                logger.error("Failed to fetch parent for file:")
                                e.printStackTrace()
                                r.error("Database error")
                                return@launch
                            }
                        }

                        // Add children (empty JSON array if there are no children)
                        media.put("children", children)

                        println(media)

                        // Convert tags to real JSON array
                        media.put("tags", JsonArray(media.getString("tags")))

                        // Return response
                        r.success(media)
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
    //  - tags (optional): JSON array, the new tags to give this media file
    post("/api/v1/media/:file/edit", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.edit")) {
                val fileId = r.pathParam("file")

                try {
                    // Fetch media file
                    val mediaRes = fetchMediaInfo(fileId)

                    // Check if it exists
                    if(mediaRes != null && mediaRes.rows.size > 0) {
                        // Fetch media info
                        val media = mediaRes.rows[0]

                        try {
                            // Resolve edit values
                            val name : String? = if(params["name"] != null) params["name"].nullIfEmpty() else media.getString("name")
                            val desc : String? = if(params["description"] != null) params["description"].nullIfEmpty() else media.getString("description")
                            val tags = if(params["tags"] != null) JsonArray(params["tags"]) else JsonArray(media.getString("tags"))

                            // Validate tags
                            var badType = false
                            var dash = false
                            var space = false
                            for(tag in tags) {
                                if(tag !is String) {
                                    badType = true
                                    break
                                } else if(tag.startsWith('-')) {
                                    dash = true
                                    break
                                } else if(tag.contains(' ')) {
                                    space = true
                                    break
                                }
                            }
                            if(badType) {
                                r.error("All tags must be strings")
                                return@launch
                            } else if(dash) {
                                r.error("Tags must not start with a dash")
                                return@launch
                            } else if(space) {
                                r.error("Tags must not contain spaces")
                                return@launch
                            }

                            try {
                                // Update media info
                                updateMediaInfo(media.getInteger("internal_id"), name, desc, tags)

                                try {
                                    // Update tags
                                    refreshTags()

                                    r.success()
                                } catch(e : Exception) {
                                    logger.error("Failed to update tags:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
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
                        val fs = vertx().fileSystem()

                        // Whether this media's files should be deleted
                        var delete = false

                        // Check if media is a child
                        if(media.getInteger("media_parent") == null) {
                            delete = true

                            try {
                                // Fetch children
                                val children = fetchMediaChildren(media.getInteger("id"))

                                // Delete children
                                for(child in children?.rows.orEmpty()) {
                                    try {
                                        val file = config.upload_location+child.getString("media_file")

                                        // Delete file
                                        if(fs.existsAwait(file))
                                            fs.deleteAwait(file)

                                        if(child.getBoolean("media_thumbnail")) {
                                            val thumbFile = config.upload_location+"thumnails/"+child.getString("media_thumbnail_file")

                                            // Delete thumbnail
                                            if(fs.existsAwait(thumbFile))
                                                fs.deleteAwait(thumbFile)
                                        }

                                        // Delete entry
                                        deleteMedia(child.getString("media_id"))

                                        try {
                                            // Delete entries linking to the file in lists
                                            deleteListItemsByMediaId(child.getInteger("id"))
                                        } catch(e : Exception) {
                                            logger.error("Failed to delete list references to child ID ${child.getString("")}:")
                                            e.printStackTrace()
                                            r.error("Database error")
                                            return@launch
                                        }
                                    } catch(e : Exception) {
                                        logger.error("Failed to delete child ID ${child.getString("")}:")
                                        e.printStackTrace()
                                        r.error("Database error")
                                        return@launch
                                    }
                                }
                            } catch(e : Exception) {
                                logger.error("Failed to fetch media children:")
                                e.printStackTrace()
                                r.error("Database error")
                                return@launch
                            }
                        } else {
                            // Check if files with the same hash exist
                            val hashMediaRes = fetchMediaByHash(media.getString("media_file_hash"))

                            if (hashMediaRes != null && hashMediaRes.rows.size < 2) {
                                delete = true
                            }
                        }

                        if(delete) {
                            // Delete media files
                            try {
                                // Delete main file
                                val file = media.getString("media_file")

                                if(fs.existsAwait(config.upload_location + file))
                                    fs.deleteAwait(config.upload_location + file)
                            } catch (e: Exception) {
                                // Failed to delete main file
                                logger.error("Failed to delete file ${media.getString("media_file")}:")
                                e.printStackTrace()
                                r.error("Internal error")
                                return@launch
                            }
                            if(media.getBoolean("media_thumbnail")) {
                                try {
                                    // Delete thumbnail file
                                    val file = media.getString("media_thumbnail_file")

                                    if (fs.existsAwait(config.upload_location + "thumbnails/" + file))
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

                            try {
                                // Delete entries linking to the file in lists
                                deleteListItemsByMediaId(media.getInteger("id"))
                            } catch(e : Exception) {
                                logger.error("Failed to delete list references to media ID $fileId:")
                                e.printStackTrace()
                                r.error("Database error")
                                return@launch
                            }

                            r.success()
                        } catch (e: Exception) {
                            logger.error("Failed to delete file entry for ID $fileId:")
                            e.printStackTrace()
                            r.error("Internal error")
                        }
                    } else {
                        r.error("File does not exist")
                    }

                    try {
                        // Update tags
                        refreshTags()
                    } catch(e : Exception) {
                        logger.error("Failed to update tags:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to delete file:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Returns all media files from a list
    // Permissions:
    //  - lists.view (only required if list is not public, otherwise no authentication or permissions are required)
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large)
    // Route params:
    //  - id: String, the alphanumeric ID of the list
    get("/api/v1/media/list/:id", domain) { r ->
        val params = r.request().params()
        val listId = r.pathParam("id")
        GlobalScope.launch(vertx().dispatcher()) {
            try {
                // Collect parameters
                val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)

                try {
                    val listRes = fetchList(listId)

                    if(listRes != null && listRes.rows.size > 0) {
                        val list = listRes.rows[0]

                        if(list.getInteger("list_visibility") == 1 || r.hasPermission("lists.view")) {
                            try {
                                // Fetch files based on list type
                                val media = when(list.getInteger("list_type")) {
                                    1 -> {
                                        // Collect source data
                                        val tags = if(list.getString("list_source_tags") == null) null else JsonArray(list.getString("list_source_tags"))
                                        val excludeTags = if(list.getString("list_source_exclude_tags") == null) null else JsonArray(list.getString("list_source_exclude_tags"))
                                        val createdBefore = list.getString("list_source_created_before")
                                        val createdAfter = list.getString("list_source_created_after")
                                        val mime = list.getString("list_source_mime")

                                        fetchMediaListByTagsAndDateRange(tags, excludeTags, createdBefore, createdAfter, mime, offset, limit, order)
                                    }
                                    else -> {
                                        fetchMediaListByListId(offset, limit, list.getInteger("id"), order)
                                    }
                                }

                                // Create JSON array of files
                                val arr = JsonArray()

                                for(file in media?.rows.orEmpty())
                                    arr.add(file)

                                // Send files
                                r.success(json {
                                    obj("files" to arr)
                                })
                            } catch(e : Exception) {
                                logger.error("Failed to fetch files:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.unauthorized()
                        }
                    } else if(r.hasPermission("lists.view")) {
                        r.error("List does not exist")
                    } else {
                        r.unauthorized()
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch list:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            } catch(e : Exception) {
                r.error("Invalid parameters")
            }
        }
    }
}