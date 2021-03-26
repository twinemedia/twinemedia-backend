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
import net.termer.twinemedia.enumeration.ListType.*
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.validation.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*
import java.time.OffsetDateTime

/**
 * Sets up all routes for creating, editing, and deleting lists
 * @since 1.0.0
 */
fun listsController() {
	for(hostname in appHostnames()) {
		// Returns all lists
		// Permissions:
		//  - lists.list
		// Parameters:
		//  - offset (optional): Integer at least 0 that sets the offset of returned results
		//  - limit (optional): Integer from 0 to 100, sets the amount of results to return
		//  - order (optional): Integer from 0 to 3, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, modified date newest to oldest, modified date oldest to newest)
		//  - type (optional): Integer from 0 to 1, the type of list to return (returns all if not specified)
		//  - media (optional): String, the alphanumeric ID of a file to check for when fetching lists (will return contains_media columns on all returned lists)
		router().get("/api/v1/lists").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.list")) {
					val listsModel = ListsModel(r.account())

					// Request validation
					val v = RequestValidator()
							.optionalParam("offset", Presets.resultOffsetValidator(), 0)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("order", IntValidator()
									.coerceMin(0)
									.coerceMax(5),
							0)
							.optionalParam("type", Presets.listTypeValidator(true), -1)
							.optionalParam("media", StringValidator())

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int
						val type = intToListType(v.parsedParam("type") as Int)
						val media = v.parsedParam("media") as String?

						try {
							// Fetch lists
							val lists = listsModel.fetchLists(offset, limit, order, type, media)

							// Create JSON array of lists
							val arr = JsonArray()

							for(list in lists)
								arr.add(list.toJson())

							// Send lists
							r.success(json {
								obj("lists" to arr)
							})
						} catch (e: Exception) {
							logger.error("Failed to fetch lists:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Searches lists using a plaintext query
		// Permissions:
		//  - lists.list
		// Parameters:
		//  - query: String, the plaintext query to search
		//  - offset (optional): Integer at least 0 that sets the offset of returned results
		//  - limit (optional): Integer from 0 to 100, sets the amount of results to return
		//  - order (optional): Integer from 0 to 3, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending)
		//  - type (optional): Integer from 0 to 1, the type of list to return (returns all if not specified)
		//  - media (optional): String, the alphanumeric ID of a file to check for when fetching lists (will return contains_media columns on all returned lists)
		//  - searchNames (optional): Bool, whether to search list names (if not specified defaults to true)
		//  - searchDescriptions (optional): Bool, whether to search list descriptions (if not specified defaults to true)
		router().get("/api/v1/lists/search").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.list")) {
					val listsModel = ListsModel(r.account())

					// Fix for an edge case on certain machines where the path params are read as query params for another route... it doesn't make sense but this fixes it
					if(r.request().params()["type"] == "lists")
						r.request().params().remove("type")

					// Request validation
					val v = RequestValidator()
							.param("query", StringValidator().trim())
							.optionalParam("offset", Presets.resultOffsetValidator(), 0)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("order", IntValidator()
									.coerceMin(0)
									.coerceMax(5),
							0)
							.optionalParam("type", Presets.listTypeValidator(true), -1)
							.optionalParam("media", StringValidator())
							.optionalParam("searchNames", BooleanValidator(), true)
							.optionalParam("searchDescriptions", BooleanValidator(), true)

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int
						val type = intToListType(v.parsedParam("type") as Int)
						val media = v.parsedParam("media") as String?
						val query = v.parsedParam("query") as String
						val searchItems = object {
								val name = v.parsedParam("searchNames") as Boolean
								val description = v.parsedParam("searchDescriptions") as Boolean
						}

						try {
							// Fetch lists
							val lists = when (query.isNotEmpty()) {
								true -> {
									listsModel.fetchListsByPlaintextQuery(query, offset, limit, order, type, media,
											searchItems.name,
											searchItems.description
									)
								}
								else -> listsModel.fetchLists(offset, limit, order, type, media)
							}

							// Create JSON array of lists
							val arr = JsonArray()

							for(list in lists)
								arr.add(list.toJson())

							// Send lists
							r.success(json {
								obj("lists" to arr)
							})
						} catch (e: Exception) {
							logger.error("Failed to fetch lists:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
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
		//  - description (max of 1024 chars) (optional): String, the list's description
		//  - sourceTags (optional): JSON array, the tags source files should have (ignored if type is not 1)
		//  - sourceCreatedBefore (optional): ISO date string, the date source files should be created before (ignored if type is not 1)
		//  - sourceCreatedAfter (optional): ISO date string, the date source files should be created after (ignored if type is not 1)
		//  - sourceMime (max of  (optional): String, the mime type files should have (ignored if type is not 1)
		router().post("/api/v1/lists/create").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.create")) {
					val listsModel = ListsModel(r.account())

					// Request validation
					val v = RequestValidator()
							.param("name", StringValidator()
									.trim()
									.maxLength(256)
									.notBlank()
									.noNewlinesOrControlChars())
							.param("type", Presets.listTypeValidator(false))
							.param("visibility", Presets.listVisibilityValidator(false))
							.optionalParam("description", StringValidator()
									.maxLength(1024)
									.trim())
							.optionalParam("sourceTags", TagsValidator())
							.optionalParam("sourceExcludeTags", TagsValidator())
							.optionalParam("sourceCreatedBefore", OffsetDateTimeValidator())
							.optionalParam("sourceCreatedAfter", OffsetDateTimeValidator())
							.optionalParam("sourceMime", StringValidator()
									.notBlank()
									.noNewlinesOrControlChars()
									.maxLength(64)
									.trim())

					if(v.validate(r)) {
						val name = v.parsedParam("name") as String
						val description = v.parsedParam("description") as String?
						val type = intToListType(v.parsedParam("type") as Int)!!
						val visibility = intToListVisibility(v.parsedParam("visibility") as Int)!!
						val sourceTags = if(type == AUTOMATICALLY_POPULATED) (v.parsedParam("sourceTags") as JsonArray?)?.toStringArray() else null
						val sourceExcludeTags = if(type == AUTOMATICALLY_POPULATED) (v.parsedParam("sourceExcludeTags") as JsonArray?)?.toStringArray() else null
						val sourceCreatedBefore = if(type == AUTOMATICALLY_POPULATED) v.parsedParam("sourceCreatedBefore") as OffsetDateTime? else null
						val sourceCreatedAfter = if(type == AUTOMATICALLY_POPULATED) v.parsedParam("sourceCreatedAfter") as OffsetDateTime? else null
						val sourceMime = if(type == AUTOMATICALLY_POPULATED) v.parsedParam("sourceMime") as String? else null

						// Generate list ID
						val id = generateString(10)

						try {
							// Create list
							listsModel.createList(id, name, description, r.userId(), visibility, type, sourceTags, sourceExcludeTags, sourceCreatedBefore, sourceCreatedAfter, sourceMime)

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
						r.error(v)
					}
				}
			}
		}

		// Fetches info about a list
		// Permissions:
		//  - lists.view (only required if list is not public, otherwise no authentication or permissions are required)
		// Route parameters:
		//  - id: String, the alphanumeric ID of the list to fetch
		router().get("/api/v1/list/:id").virtualHost(hostname).handler { r ->
			val id = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				// Intentionally do not associate an account with this model in order to avoid some users not being able to view public lists
				val listsModel = ListsModel()

				try {
					// Fetch list
					val listRes = listsModel.fetchListInfo(id)

					if(listRes.count() > 0) {
						val list = listRes.iterator().next()

						// Check if the user has permission to view the list, or it's a public list
						if(list.visibility == ListVisibility.PUBLIC || (r.hasPermission("lists.view") && (list.creator == r.userId() || r.hasPermission("lists.view.all")))) {
							// Send list and success
							r.success(list.toJson())
						} else if(r.authenticated()) {
							r.error("List does not exist")
						} else {
							r.unauthorized()
						}
					} else if(r.hasPermission("lists.view")) {
						r.error("List does not exist")
					} else {
						r.unauthorized()
					}
				} catch (e: Exception) {
					logger.error("Failed to fetch list:")
					e.printStackTrace()
					r.error("Database error")
				}
			}
		}

		// Edits a list
		// Permissions:
		//  - lists.edit
		// Parameters:
		//  - name: String, the list's name
		//  - type: Integer, the list type
		//  - visibility: Integer, the list visibility
		//  - description (optional): String, the list's description
		//  - sourceTags (optional): JSON array, the tags source files should have (ignored if type is not 1)
		//  - sourceCreatedBefore (optional): ISO date string, the date source files should be created before (ignored if type is not 1)
		//  - sourceCreatedAfter (optional): ISO date string, the date source files should be created after (ignored if type is not 1)
		//  - sourceMime (optional): String, the mime type files should have (ignored if type is not 1)
		// Route parameters:
		//  - id: String, the alphanumeric ID of the list to edit
		router().post("/api/v1/list/:id/edit").virtualHost(hostname).handler { r ->
			val id = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.edit")) {
					val listsModel = ListsModel(r.account())

					try {
						// Fetch list
						val listRes = listsModel.fetchList(id)

						// Check if it exists
						if(listRes.count() > 0) {
							val list = listRes.iterator().next()

							// Check if list was created by the user and deny if it's not and the user is lacking .all perms
							if(list.creator != r.userId() && !r.hasPermission("lists.edit.all")) {
								r.unauthorized()
								return@launch
							}

							val oldType = list.type

							// Request validation
							val v = RequestValidator()
									.param("name", StringValidator()
											.trim()
											.maxLength(256)
											.notBlank()
											.noNewlinesOrControlChars())
									.param("type", Presets.listTypeValidator(false))
									.param("visibility", Presets.listVisibilityValidator(false))
									.optionalParam("description", StringValidator()
											.maxLength(1024)
											.trim())
									.optionalParam("sourceTags", TagsValidator())
									.optionalParam("sourceExcludeTags", TagsValidator())
									.optionalParam("sourceCreatedBefore", OffsetDateTimeValidator())
									.optionalParam("sourceCreatedAfter", OffsetDateTimeValidator())
									.optionalParam("sourceMime", StringValidator()
											.notBlank()
											.noNewlinesOrControlChars()
											.maxLength(64)
											.trim())

							if(v.validate(r)) {
								val name = v.parsedParam("name") as String
								val description = v.parsedParam("description") as String?
								val type = intToListType(v.parsedParam("type") as Int)!!
								val visibility = intToListVisibility(v.parsedParam("visibility") as Int)!!
								val sourceTags = if(type == AUTOMATICALLY_POPULATED) (v.parsedParam("sourceTags") as JsonArray?)?.toStringArray() else null
								val sourceExcludeTags = if(type == AUTOMATICALLY_POPULATED) (v.parsedParam("sourceExcludeTags") as JsonArray?)?.toStringArray() else null
								val sourceCreatedBefore = if(type == AUTOMATICALLY_POPULATED) v.parsedParam("sourceCreatedBefore") as OffsetDateTime? else null
								val sourceCreatedAfter = if(type == AUTOMATICALLY_POPULATED) v.parsedParam("sourceCreatedAfter") as OffsetDateTime? else null
								val sourceMime = if(type == AUTOMATICALLY_POPULATED) v.parsedParam("sourceMime") as String? else null

								// Get list ID
								val internalId = list.internalId

								try {
									if(type == STANDARD) {
										// Update list
										listsModel.updateListToNormal(internalId, name, description, visibility)

										// Send success
										r.success()
									} else {
										// If old type is STANDARD, delete all list items
										if(oldType == STANDARD)
											listsModel.deleteListItemsByListId(list.internalId)

										// Update list
										listsModel.updateListToAutomaticallyPopulated(internalId, name, description, visibility, sourceTags, sourceExcludeTags, sourceCreatedBefore, sourceCreatedAfter, sourceMime)

										// Send success
										r.success()
									}
								} catch (e: Exception) {
									logger.error("Failed to update list:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								r.error(v)
							}
						} else {
							r.error("List does not exist")
						}
					} catch (e: Exception) {
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
		router().post("/api/v1/list/:id/delete").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.delete")) {
					val listsModel = ListsModel()
					val id = r.pathParam("id")

					try {
						// Check if the list exists
						val listRes = listsModel.fetchList(id)

						if(listRes.count() > 0) {
							val list = listRes.iterator().next()

							// Check if list was created by the user
							if(list.creator != r.userId() && !r.hasPermission("lists.delete.all")) {
								r.unauthorized()
								return@launch
							}

							// Delete the list
							listsModel.deleteList(id)

							// Delete all if its items if it's a standard list
							if(list.type == STANDARD)
								listsModel.deleteListItemsByListId(list.internalId)

							r.success()
						} else {
							r.error("List does not exist")
						}
					} catch (e: Exception) {
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
		router().post("/api/v1/list/:id/add/:media").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.add")) {
					val mediaModel = MediaModel(r.account())
					val listsModel = ListsModel()
					val id = r.pathParam("id")
					val mediaId = r.pathParam("media")

					try {
						// Check if the list exists
						val listRes = listsModel.fetchList(id)

						if(listRes.count() > 0) {
							val list = listRes.iterator().next()

							// Check if list was created by the user
							if(list.creator != r.userId() && !r.hasPermission("lists.add.all")) {
								r.unauthorized()
								return@launch
							}

							// Check if list is STANDARD
							if(list.type == STANDARD) {
								try {
									// Check if media exists
									val mediaRes = mediaModel.fetchMedia(mediaId)

									if(mediaRes.count() > 0) {
										val media = mediaRes.iterator().next()

										try {
											// Check if item is already added to list
											val containsRes = listsModel.fetchListContainsItem(list.internalId, media.internalId)

											if(containsRes) {
												// Already has the item so nothing needs to be done
												r.success()
											} else {
												try {
													// Create item entry
													listsModel.createListItem(list.internalId, media.internalId)

													// Send success
													r.success()
												} catch (e: Exception) {
													logger.error("Failed to create new list item:")
													e.printStackTrace()
													r.error("Database error")
												}
											}
										} catch (e: Exception) {
											logger.error("Failed to fetch whether list contains item:")
											e.printStackTrace()
											r.error("Database error")
										}
									} else {
										r.error("File does not exist")
									}
								} catch (e: Exception) {
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
					} catch (e: Exception) {
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
		router().post("/api/v1/list/:id/remove/:media").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("lists.remove")) {
					val mediaModel = MediaModel(r.account())
					val listsModel = ListsModel()
					val id = r.pathParam("id")
					val mediaId = r.pathParam("media")

					try {
						// Check if the list exists
						val listRes = listsModel.fetchList(id)

						if(listRes.count() > 0) {
							val list = listRes.iterator().next()

							// Check if list was created by the user
							if(list.creator != r.userId() && !r.hasPermission("lists.remove.all")) {
								r.unauthorized()
								return@launch
							}

							// Check if list is STANDARD
							if(list.type == STANDARD) {
								try {
									// Check if media exists
									val mediaRes = mediaModel.fetchMedia(mediaId)

									if(mediaRes.count() > 0) {
										val media = mediaRes.iterator().next()

										try {
											// Delete item entry
											listsModel.deleteListItemByListAndFile(list.internalId, media.internalId)

											// Send success
											r.success()
										} catch (e: Exception) {
											logger.error("Failed to delete list item:")
											e.printStackTrace()
											r.error("Database error")
										}
									} else {
										r.error("File does not exist")
									}
								} catch (e: Exception) {
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
					} catch (e: Exception) {
						logger.error("Failed to fetch list:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}
	}
}