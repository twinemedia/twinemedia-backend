package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.TagsModel
import net.termer.twinemedia.util.*

/**
 * Sets up all routes for retrieving tags and tag-related information
 * @since 1.0
 */
fun tagsController() {
	for(hostname in appHostnames()) {
		// Returns tags, optionally based on a query (which supports % wildcards)
		// Permissions:
		//  - tags.list
		// Parameters:
		//  - query (optional): String, the tag pattern to search for, can use % as a wildcard character
		//  - offset: Integer at least 0 that sets the offset of returned results
		//  - limit: Integer from 0 to 100, sets the amount of results to return
		//  - order: Integer from 0 to 5, denotes the type of sorting to use (alphabetically ascending, alphabetically descending, tag length ascending, tag length descending, tag uses ascending, tag uses descending)
		router().get("/api/v1/tags").virtualHost(hostname).handler { r ->
			val params = r.request().params()
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("tags.list")) {
					val tagsModel = TagsModel(r.account())

					try {
						// Collect parameters
						val query = if(params.contains("query")) params["query"] else ""
						val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
						val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
						val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)

						try {
							val tags = when(query.isEmpty()) {
                                true -> tagsModel.fetchAllTags(offset, limit, order)
								else -> tagsModel.fetchTagsByTerm(query, offset, limit, order)
							}

							// Create JSON array of tags
							val arr = JsonArray()

							for(tag in tags.rows.orEmpty())
								arr.add(tag)

							// Send tags
							r.success(JsonObject().put("tags", arr))
						} catch(e: Exception) {
							logger.error("Failed to fetch tags:")
							e.printStackTrace()
							r.error("Database error")
						}
					} catch(e: Exception) {
						r.error("Invalid parameters")
					}
				}
			}
		}

		// Returns info about a tag
		// Permissions:
		//  - tags.info
		// Route parameters:
		//  - tag: String, the tag to fetch info for
		router().get("/api/v1/tag/:tag/info").virtualHost(hostname).handler { r ->
			val tag = r.pathParam("tag")
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("tags.info")) {
					val tagsModel = TagsModel(r.account())

					try {
						val infoRes = tagsModel.fetchTagInfo(tag)

						// Check if a row has been returned
						if(infoRes != null && infoRes.rows.size > 0) {
							val info = infoRes.rows[0]

							r.success(info)
						} else {
							// This really shouldn't happen, but catch it if it does
							r.error("Tag not found")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch tag info:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}
	}
}