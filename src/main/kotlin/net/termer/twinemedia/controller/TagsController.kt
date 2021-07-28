package net.termer.twinemedia.controller

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.TagsModel
import net.termer.twinemedia.util.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Sets up all routes for retrieving tags and tag-related information
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun tagsController() {
	for(hostname in appHostnames()) {
		// Returns tags, optionally based on a query (which supports % wildcards)
		// Permissions:
		//  - tags.list
		// Parameters:
		//  - query: (optional) String, the tag pattern to search for, can use % as a wildcard character
		//  - offset: (optional) Integer at least 0 that sets the offset of returned results
		//  - limit: (optional) Integer from 0 to 100, sets the amount of results to return
		//  - order: (optional) Integer from 0 to 5, denotes the type of sorting to use (alphabetically ascending, alphabetically descending, tag length ascending, tag length descending, tag uses ascending, tag uses descending)
		router().get("/api/v1/tags").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("tags.list")) {
					val tagsModel = TagsModel(r.account())

					// Request validation
					val v = RequestValidator()
							.offsetLimitOrder(5)
							.optionalParam("query", StringValidator()
									.noNewlinesOrControlChars()
									.trim(), "")

					if(v.validate(r)) {
						// Collect parameters
						val query = v.parsedParam("query") as String
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int

						try {
							val tags = if(query.isEmpty())
								tagsModel.fetchAllTags(offset, limit, order)
							else
								tagsModel.fetchTagsByTerm(query, offset, limit, order)

							// Send tags
							r.success(json {obj(
									"tags" to tags.toJsonArray()
							)})
						} catch(e: Exception) {
							logger.error("Failed to fetch tags:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
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
						if(infoRes.count() > 0) {
							val info = infoRes.first()

							r.success(info.toJson())
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