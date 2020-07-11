package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.ListTypes.AUTOMATICALLY_POPULATED
import net.termer.twinemedia.util.ListTypes.STANDARD
import java.text.SimpleDateFormat

/**
 * Sets up all routes for creating, editing, and deleting lists
 * @since 1.0
 */
fun listsController() {
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val domain = appDomain()

    // Returns all lists
    // Permissions:
    //  - lists.list
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - order: Integer from 0 to 3, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending)
    //  - type (optional): Integer from 0 to 1, the type of list to return (returns all if not specified)
    //  - media (optional): String, the alphanumeric ID of a file to check for when fetching lists (will return contains_media columns on all returned lists)
    get("/api/v1/lists", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 3)
                    val type = (if(params.contains("type")) params["type"].toInt() else -1).coerceIn(-1, 1)
                    val media = params["media"]

                    try {
                        // Fetch lists
                        val lists = fetchLists(offset, limit, order, type, media)

                        // Create JSON array of lists
                        val arr = JsonArray()

                        for(list in lists?.rows.orEmpty())
                            arr.add(list)

                        // Send lists
                        r.success(json {
                            obj("lists" to arr)
                        })
                    } catch(e : Exception) {
                        logger.error("Failed to fetch lists:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Searches lists using a plaintext query
    // Permissions:
    //  - lists.list
    // Parameters:
    //  - query: String, the plaintext query to search
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - order: Integer from 0 to 3, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending)
    //  - type (optional): Integer from 0 to 1, the type of list to return (returns all if not specified)
    //  - media (optional): String, the alphanumeric ID of a file to check for when fetching lists (will return contains_media columns on all returned lists)
    //  - searchNames (optional): Bool, whether to search list names (if not specified defaults to true)
    //  - searchDescriptions (optional): Bool, whether to search list descriptions (if not specified defaults to true)
    get("/api/v1/lists/search", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)
                    // Fix for an edge case on certain machines where the path params are read as query params for another route... it doesn't make sense but this fixes it
                    if(params["type"] == "lists")
                        params.remove("type")
                    val type = (if(params.contains("type")) params["type"].toInt() else -1).coerceIn(-1, 1)
                    val media = params["media"]
                    val query = if(params.contains("query")) params["query"] else ""
                    val searchItems = json {
                        obj(
                                "name" to if (params.contains("searchNames")) params["searchNames"]?.toBoolean() else true,
                                "description" to if (params.contains("searchDescriptions")) params["searchDescriptions"]?.toBoolean() else true
                        )
                    }

                    try {
                        // Fetch lists
                        val lists = when(query.isNotEmpty()) {
                            true -> {
                                fetchListsByPlaintextQuery(query, offset, limit, order, type, media,
                                        searchItems.getBoolean("name"),
                                        searchItems.getBoolean("description")
                                )
                            }
                            else -> fetchLists(offset, limit, order, type, media)
                        }

                        // Create JSON array of lists
                        val arr = JsonArray()

                        for(list in lists?.rows.orEmpty()) {
                            // Convert tags if not null
                            if(list.getString("source_tags") != null)
                                list.put("source_tags", JsonArray(list.getString("source_tags")))
                            if(list.getString("source_exclude_tags") != null)
                                list.put("source_exclude_tags", JsonArray(list.getString("source_exclude_tags")))

                            arr.add(list)
                        }

                        // Send lists
                        r.success(json {
                            obj("lists" to arr)
                        })
                    } catch(e : Exception) {
                        logger.error("Failed to fetch lists:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Creates a new list
    // Permissions:
    //  - lists.create
    // Parameters:
    //  - name: String, the list's name
    //  - type: Integer, the list type
    //  - visibility: Integer, the list visibility
    //  - description (optional): String, the list's description
    //  - sourceTags (optional): JSON array, the tags source files should have (ignored if type is not 1)
    //  - sourceCreatedBefore (optional): ISO date string, the date source files should be created before (ignored if type is not 1)
    //  - sourceCreatedAfter (optional): ISO date string, the date source files should be created after (ignored if type is not 1)
    //  - sourceMime (optional): String, the mime type files should have (ignored if type is not 1)
    post("/api/v1/lists/create", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.create")) {
                if(params.contains("name") && params.contains("type") && params.contains("visibility")) {
                    try {
                        val name = if(params["name"].length > 256) params["name"].substring(0, 256) else params["name"]
                        val description = if(params.contains("description"))
                            if(params["description"].length > 1024) params["description"].substring(0, 1024) else params["description"].nullIfEmpty()
                        else
                            null
                        val type = params["type"].toInt()
                        val visibility = params["visibility"].toInt()
                        val sourceTags = if(type == AUTOMATICALLY_POPULATED && params.contains("sourceTags")) JsonArray(params["sourceTags"]) else null
                        val sourceExcludeTags = if(type == AUTOMATICALLY_POPULATED && params.contains("sourceExcludeTags")) JsonArray(params["sourceExcludeTags"]) else null
                        val sourceCreatedBefore = if(type == AUTOMATICALLY_POPULATED && params.contains("sourceCreatedBefore")) simpleDateFormat.format(simpleDateFormat.parse(params["sourceCreatedBefore"])) else null
                        val sourceCreatedAfter = if(type == AUTOMATICALLY_POPULATED && params.contains("sourceCreatedAfter")) simpleDateFormat.format(simpleDateFormat.parse(params["sourceCreatedAfter"])) else null
                        val sourceMime = if(type == AUTOMATICALLY_POPULATED && params.contains("sourceMime")) params["sourceMime"] else null

                        // Validate tags
                        val tags = JsonArray().also {
                                    if(sourceTags != null)
                                        it.addAll(sourceTags)
                                    if(sourceExcludeTags != null)
                                        it.addAll(sourceExcludeTags)
                        }
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

                        // Generate list ID
                        val id = generateString(10)

                        if(isValidListType(type)) {
                            try {
                                // Create list
                                createList(id, name, description, r.userId(), visibility, type, sourceTags, sourceExcludeTags, sourceCreatedBefore, sourceCreatedAfter, sourceMime)

                                // Send ID
                                r.success(json {
                                    obj("id" to id)
                                })
                            } catch (e: Exception) {
                                logger.error("Failed to create list:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("Invalid list type")
                        }
                    } catch(e : Exception) {
                        // Invalid tags JSON array, invalid type int, or invalid date string
                        r.error("Invalid parameters")
                    }
                } else {
                    r.error("Must provide the following values: name, type")
                }
            }
        }
    }

    // Fetches info about a list
    // Permissions:
    //  - lists.view (only required if list is not public, otherwise no authentication or permissions are required)
    // Route parameters:
    //  - id: String, the alphanumeric ID of the list to delete
    get("/api/v1/list/:id", domain) { r ->
        val id = r.pathParam("id")
        GlobalScope.launch(vertx().dispatcher()) {
            try {
                // Fetch list
                val listRes = fetchListInfo(id)

                if(listRes != null && listRes.rows.size > 0) {
                    val list = listRes.rows[0]

                    // Check if the user has permission to view the list, or it's a public list
                    if(list.getInteger("visibility") == 1 || r.hasPermission("lists.view")) {
                        // Convert tags if not null
                        if(list.getString("source_tags") != null)
                            list.put("source_tags", JsonArray(list.getString("source_tags")))
                        if(list.getString("source_exclude_tags") != null)
                            list.put("source_exclude_tags", JsonArray(list.getString("source_exclude_tags")))

                        // Send list and success
                        r.success(list)
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
        }
    }

    post("/api/v1/list/:id/edit", domain) { r ->
        val id = r.pathParam("id")
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.edit")) {
                try {
                    // Fetch list
                    val listRes = fetchList(id)

                    // Check if it exists
                    if (listRes != null && listRes.rows.size > 0) {
                        val list = listRes.rows[0]

                        try {
                            val oldType = list.getInteger("list_type")

                            val name = if (params["name"].length > 256) params["name"].substring(0, 256) else params["name"]
                            val description = if (params.contains("description"))
                                when {
                                    params["description"].length > 1024 -> params["description"].substring(0, 1024)
                                    params["description"].isEmpty() -> null
                                    else -> params["description"]
                                }
                            else
                                null
                            val type = if(params.contains("type")) params["type"].toInt() else oldType
                            val visibility = if(params.contains("visibility")) params["visibility"].toInt() else list.getInteger("list_visibility")

                            // Generate list ID
                            val id = list.getInteger("id")

                            if (isValidListType(type)) {
                                try {
                                    if(type == STANDARD) {
                                        // Update list
                                        updateListToNormal(id, name, description, visibility)

                                        // Send success
                                        r.success()
                                    } else {
                                        // If old type is 0, delete all list items
                                        if(oldType == 0)
                                            deleteListItemsByListId(list.getInteger("id"))

                                        val sourceTags = if(params.contains("sourceTags"))
                                            JsonArray(params["sourceTags"])
                                        else if(list.getString("list_source_tags") == null)
                                            null
                                        else
                                            JsonArray(list.getString("list_source_tags"))

                                        val sourceExcludeTags = if(params.contains("sourceExcludeTags"))
                                            JsonArray(params["sourceExcludeTags"])
                                        else if(list.getString("list_source_exclude_tags") == null)
                                            null
                                        else
                                            JsonArray(list.getString("list_source_tags"))

                                        val sourceCreatedBefore = if(params.contains("sourceCreatedBefore"))
                                            if(params["sourceCreatedBefore"].isEmpty()) {
                                                null
                                            } else {
                                                simpleDateFormat.format(simpleDateFormat.parse(params["sourceCreatedBefore"]))
                                            }
                                        else
                                            list.getString("list_source_created_before")

                                        val sourceCreatedAfter = if(params.contains("sourceCreatedAfter"))
                                            if(params["sourceCreatedAfter"].isEmpty()) {
                                                null
                                            } else {
                                                simpleDateFormat.format(simpleDateFormat.parse(params["sourceCreatedAfter"]))
                                            }
                                        else
                                            list.getString("list_source_created_after")

                                        val sourceMime = if(params.contains("sourceMime"))
                                            params["sourceMime"].nullIfEmpty()
                                        else
                                            list.getString("list_source_mime")

                                        // Validate tags
                                        val tags = JsonArray().also {
                                            if (sourceTags != null)
                                                it.addAll(sourceTags)
                                            if (sourceExcludeTags != null)
                                                it.addAll(sourceExcludeTags)
                                        }
                                        var badType = false
                                        var dash = false
                                        var space = false
                                        for (tag in tags) {
                                            if (tag !is String) {
                                                badType = true
                                                break
                                            } else if (tag.startsWith('-')) {
                                                dash = true
                                                break
                                            } else if (tag.contains(' ')) {
                                                space = true
                                                break
                                            }
                                        }
                                        if(badType) {
                                            r.error("All tags must be strings")
                                            return@launch
                                        } else if (dash) {
                                            r.error("Tags must not start with a dash")
                                            return@launch
                                        } else if (space) {
                                            r.error("Tags must not contain spaces")
                                            return@launch
                                        }

                                        // Update list
                                        updateListToAutomaticallyPopulated(id, name, description, visibility, sourceTags, sourceExcludeTags, sourceCreatedBefore, sourceCreatedAfter, sourceMime)

                                        // Send success
                                        r.success()
                                    }
                                } catch (e: Exception) {
                                    logger.error("Failed to update list:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
                            } else {
                                r.error("Invalid list type")
                            }
                        } catch (e: Exception) {
                            // Invalid tags JSON array, invalid type int, or invalid date string
                            r.error("Invalid parameters")
                        }
                    } else {
                        r.error("List does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch list:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Deletes a list and all of its items (if it is a standard list)
    // Permissions:
    //  - lists.delete
    // Route parameters:
    //  - id: String, the alphanumeric ID of the list to delete
    post("/api/v1/list/:id/delete", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.delete")) {
                val id = r.pathParam("id")

                try {
                    // Check if the list exists
                    val listRes = fetchList(id)

                    if(listRes != null && listRes.rows.size > 0) {
                        val list = listRes.rows[0]

                        // Delete the list
                        deleteList(id)

                        // Delete all if its items if it's a standard list
                        if(list.getInteger("list_type") == STANDARD)
                            deleteListItemsByListId(list.getInteger("id"))

                        r.success()
                    } else {
                        r.error("List does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch list:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Adds an item to a list
    // Permissions:
    //  - lists.add
    // Route parameters:
    //  - id: String, the alphanumeric ID of the list to add the item to
    //  - media: String, the alphanumeric ID of the file to add to the list
    post("/api/v1/list/:id/add/:media", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.add")) {
                val id = r.pathParam("id")
                val mediaId = r.pathParam("media")

                try {
                    // Check if the list exists
                    val listRes = fetchList(id)

                    if(listRes != null && listRes.rows.size > 0) {
                        val list = listRes.rows[0]

                        // Check if list is STANDARD
                        if(list.getInteger("list_type") == STANDARD) {
                            try {
                                // Check if media exists
                                val mediaRes = fetchMedia(mediaId)

                                if (mediaRes != null && mediaRes.rows.size > 0) {
                                    val media = mediaRes.rows[0]

                                    try {
                                        // Check if item is already added to list
                                        val containsRes = fetchListContainsItem(list.getInteger("id"), media.getInteger("id"))

                                        if (containsRes != null && containsRes.rows[0].getBoolean("contains_media")) {
                                            // Already has the item so nothing needs to be done
                                            r.success()
                                        } else {
                                            try {
                                                // Create item entry
                                                createListItem(list.getInteger("id"), media.getInteger("id"))

                                                // Send success
                                                r.success()
                                            } catch(e : Exception) {
                                                logger.error("Failed to create new list item:")
                                                e.printStackTrace()
                                                r.error("Database error")
                                            }
                                        }
                                    } catch(e : Exception) {
                                        logger.error("Failed to fetch whether list contains item:")
                                        e.printStackTrace()
                                        r.error("Database error")
                                    }
                                } else {
                                    r.error("File does not exist")
                                }
                            } catch(e : Exception) {
                                logger.error("Failed to fetch media to add to list:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("List must be standard type")
                        }
                    } else {
                        r.error("List does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch list:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Removes an item from a list
    // Permissions:
    //  - lists.remove
    // Route parameters:
    //  - id: String, the alphanumeric ID of the list to remove the item from
    //  - media: String, the alphanumeric ID of the file to remove from the list
    post("/api/v1/list/:id/remove/:media", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.remove")) {
                val id = r.pathParam("id")
                val mediaId = r.pathParam("media")

                try {
                    // Check if the list exists
                    val listRes = fetchList(id)

                    if(listRes != null && listRes.rows.size > 0) {
                        val list = listRes.rows[0]

                        // Check if list is STANDARD
                        if(list.getInteger("list_type") == STANDARD) {
                            try {
                                // Check if media exists
                                val mediaRes = fetchMedia(mediaId)

                                if (mediaRes != null && mediaRes.rows.size > 0) {
                                    val media = mediaRes.rows[0]

                                    try {
                                        // Delete item entry
                                        deleteListItemByListAndFile(list.getInteger("id"), media.getInteger("id"))

                                        // Send success
                                        r.success()
                                    } catch(e : Exception) {
                                        logger.error("Failed to delete list item:")
                                        e.printStackTrace()
                                        r.error("Database error")
                                    }
                                } else {
                                    r.error("File does not exist")
                                }
                            } catch(e : Exception) {
                                logger.error("Failed to fetch media to remove from list:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("List must be standard type")
                        }
                    } else {
                        r.error("List does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch list:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }
}