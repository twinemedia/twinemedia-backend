package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.Twine
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.fetchLists
import net.termer.twinemedia.model.fetchListsByPlaintextQuery
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.protectWithPermission
import net.termer.twinemedia.util.success

/**
 * Sets up all routes for creating, editing, and deleting lists
 * @since 1.0
 */
fun listsController() {
    val domain = Twine.domains().byName(config.domain).domain()

    // Returns all lists
    // Permissions:
    //  - lists.list
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - order: Integer from 0 to 3, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending)
    get("/api/v1/lists", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 3)

                    try {
                        // Fetch lists
                        val lists = fetchLists(offset, limit, order)

                        // Create JSON array of lists
                        val arr = JsonArray()

                        for(list in lists?.rows.orEmpty())
                            arr.add(list)

                        // Send lists
                        r.success(JsonObject().put("lists", arr))
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
    //  - searchNames: Bool, whether to search list names
    //  - searchDescriptions: Bool, whether to search list descriptions
    get("/api/v1/lists/search", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("lists.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)
                    val query = if(params.contains("query")) params["query"] else ""
                    val searchItems = JsonObject()
                            .put("name", if(params.contains("searchNames")) params["searchNames"].toBoolean() else true)
                            .put("description", if(params.contains("searchDescriptions")) params["searchDescriptions"].toBoolean() else true)

                    try {
                        // Fetch lists
                        val lists = when(query.isNotEmpty()) {
                            true -> {
                                fetchListsByPlaintextQuery(query, offset, limit, order,
                                        searchItems.getBoolean("name"),
                                        searchItems.getBoolean("description")
                                )
                            }
                            else -> fetchLists(offset, limit, order)
                        }

                        // Create JSON array of lists
                        val arr = JsonArray()

                        for(list in lists?.rows.orEmpty())
                            arr.add(list)

                        // Send lists
                        r.success(JsonObject().put("lists", arr))
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
}