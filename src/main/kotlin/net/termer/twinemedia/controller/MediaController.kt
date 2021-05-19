package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.db.scheduleTagsViewRefresh
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.validation.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Sets up all routes for retrieving and modifying file info + processing files
 * @since 1.0.0
 */
fun mediaController() {
	for(hostname in appHostnames()) {
		// Returns all media files
		// Permissions:
		//  - files.list
		// Parameters:
		//  - offset: (optional) Integer at least 0 that sets the offset of returned results
		//  - limit: (optional) Integer from 0 to 100, sets the amount of results to return
		//  - mime: (optional) String, the mime pattern to search for, can use % as a wildcard character
		//  - order: (optional) Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large, modified date newest to oldest, modified date oldest to newest)
		router().get("/api/v1/media").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.list")) {
					val mediaModel = MediaModel(r.account())

					// Request validation
					val v = RequestValidator()
							.optionalParam("offset", Presets.resultOffsetValidator(), 0)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("mime", Presets.mimeValidator(true), "%")
							.optionalParam("order", IntValidator()
									.min(0)
									.max(7), 0)

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val mime = v.parsedParam("mime") as String
						val order = v.parsedParam("order") as Int

						try {
							// Fetch files
							val media = mediaModel.fetchMediaList(offset, limit, mime, order)

							// Create JSON array of files
							val arr = JsonArray()

							for(file in media)
								arr.add(file.toJson())

							// Send files
							r.success(JsonObject().put("files", arr))
						} catch(e: Exception) {
							logger.error("Failed to fetch files:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Searches media files using a plaintext query
		// Permissions:
		//  - files.list
		// Parameters:
		//  - query: (optional) String, the plaintext query to search
		//  - offset: (optional) Integer at least 0 that sets the offset of returned results
		//  - limit: (optional) Integer from 0 to 100, sets the amount of results to return
		//  - mime: (optional) String, the mime pattern to search for, can use % as a wildcard character
		//  - order: (optional) Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large, modified date newest to oldest, modified date oldest to newest)
		//  - searchNames (optional): Bool, whether to search file names (if not specified defaults to true)
		//  - searchFilenames (optional): Bool, whether to search file filenames (if not specified defaults to true)
		//  - searchTags (optional): Bool, whether to search file tags (if not specified defaults to true)
		//  - searchDescriptions (optional): Bool, whether to search file descriptions (if not specified defaults to true)
		router().get("/api/v1/media/search").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.list")) {
					val mediaModel = MediaModel(r.account())

					// Request validation
					val v = RequestValidator()
							.optionalParam("query", StringValidator())
							.optionalParam("offset", Presets.resultOffsetValidator(), 0)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("mime", Presets.mimeValidator(true), "%")
							.optionalParam("order", IntValidator()
									.min(0)
									.max(7), 0)
							.optionalParam("searchNames", BooleanValidator(), true)
							.optionalParam("searchFilenames", BooleanValidator(), true)
							.optionalParam("searchTags", BooleanValidator(), true)
							.optionalParam("searchDescriptions", BooleanValidator(), true)

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val mime = v.parsedParam("mime") as String
						val order = v.parsedParam("order") as Int
						val query = v.parsedParam("query") as String?
						val searchNames = v.parsedParam("searchNames") as Boolean
						val searchFilenames = v.parsedParam("searchFilenames") as Boolean
						val searchTags = v.parsedParam("searchTags") as Boolean
						val searchDescriptions = v.parsedParam("searchDescriptions") as Boolean

						try {
							// Fetch files
							val media = if(query == null)
								mediaModel.fetchMediaList(offset, limit, mime, order)
							else
								mediaModel.fetchMediaByPlaintextQuery(query, offset, limit, order, mime, searchNames, searchFilenames, searchTags, searchDescriptions)

							// Create JSON array of files
							val arr = JsonArray()

							for(file in media)
								arr.add(file.toJson())

							// Send files
							r.success(JsonObject().put("files", arr))
						} catch(e: Exception) {
							logger.error("Failed to fetch files:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
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
		//  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large, modified date newest to oldest, modified date oldest to newest)
		router().get("/api/v1/media/tags").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.list")) {
					val mediaModel = MediaModel(r.account())

					// Request validation
					val v = RequestValidator()
							.param("tags", JsonArrayValidator())
							.optionalParam("excludeTags", JsonArrayValidator())
							.optionalParam("offset", Presets.resultOffsetValidator(), 100)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("order", IntValidator()
									.min(0)
									.max(7), 0)
							.optionalParam("mime", Presets.mimeValidator(true), "%")

					if(v.validate(r)) {
						try {
							val tags = (v.parsedParam("tags") as JsonArray).toStringArray()
							val excludeTags = (v.parsedParam("excludeTags") as JsonArray?)?.toStringArray()
							val offset = v.parsedParam("offset") as Int
							val limit = v.parsedParam("limit") as Int
							val order = v.parsedParam("order") as Int
							val mime = v.parsedParam("mime") as String

							try {
								// Fetch files
								val filesRes = mediaModel.fetchMediaListByTags(tags, excludeTags, mime, order, offset, limit)

								// Compose response
								val files = JsonArray()
								for(row in filesRes)
									files.add(row.toJson())

								r.success(JsonObject().put("files", files))
							} catch(e: Exception) {
								logger.error("Failed to fetch file list by tags:")
								e.printStackTrace()
								r.error("Database error")
							}
						} catch(e: Exception) {
							// Tags are not a JSON array
							r.error("Tags must be a JSON array")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Returns info about the specified media file
		// Permissions:
		//  - files.view
		// Route parameters:
		//  - file: String, the alphanumeric ID of the requested media file
		router().get("/api/v1/media/:file").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.view")) {
					val mediaModel = MediaModel(r.account())
					val fileId = r.pathParam("file")

					try {
						// Fetch media file
						val mediaRes = mediaModel.fetchMediaInfo(fileId)

						// Check if it exists
						if(mediaRes.count() > 0) {
							// Fetch media info
							val media = mediaRes.iterator().next()
							val mediaJson = media.toJson()

							// Fetch internal values
							val id = media.internalId
							val parent = media.internalParent

							// JSON array of children
							val children = JsonArray()

							if(parent == null) {
								try {
									// Fetch children
									val childrenRes = mediaModel.fetchMediaChildrenInfo(id)

									// Add them
									for(child in childrenRes)
										children.add(child.toJson())

								} catch(e: Exception) {
									logger.error("Failed to fetch children for file:")
									e.printStackTrace()
									r.error("Database error")
									return@launch
								}
							} else {
								try {
									// Fetch parent
									val parentRes = mediaModel.fetchMediaInfo(parent)
									if(parentRes.count() > 0) {
										mediaJson.put("parent", parentRes.iterator().next().toJson())
									} else {
										mediaJson.put("parent", null as String?)
									}
								} catch(e: Exception) {
									logger.error("Failed to fetch parent for file:")
									e.printStackTrace()
									r.error("Database error")
									return@launch
								}
							}

							// Add children (empty JSON array if there are no children)
							mediaJson.put("children", children)

							// Return response
							r.success(mediaJson)
						} else {
							r.error("File does not exist")
						}
					} catch(e: Exception) {
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
		//  - name (optional): String, the new name of the media file, can be null (or empty to be null)
		//  - desc (optional): String, the new description of the media file, can be null (or empty to be null)
		//  - tags (optional): JSON array, the new tags to give this media file
		router().post("/api/v1/media/:file/edit").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.edit")) {
					val mediaModel = MediaModel(r.account())
					val fileId = r.pathParam("file")

					try {
						// Fetch media file
						val mediaRes = mediaModel.fetchMedia(fileId)

						// Check if it exists
						if(mediaRes.count() > 0) {
							// Fetch media info
							val media = mediaRes.iterator().next()

							// Check if media was created by the user
							if(media.creator != r.userId() && !r.hasPermission("files.edit.all")) {
								r.unauthorized()
								return@launch
							}

							// Request validation
							val v = RequestValidator()
									.optionalParam("filename", StringValidator()
											.trim()
											.minLength(1)
											.maxLength(256), media.filename)
									.optionalParam("name", StringValidator()
											.trim()
											.maxLength(256), media.name)
									.optionalParam("description", StringValidator()
											.maxLength(1024), media.description)
									.optionalParam("tags", TagsValidator(), media.tags.toJsonArray())

							if(v.validate(r)) {
								// Resolve edit values
								val filename = v.parsedParam("filename") as String
								val name = (v.parsedParam("name") as String?)?.nullIfEmpty()
								val desc = (v.parsedParam("description") as String?)?.nullIfEmpty()
								val tags = (v.parsedParam("tags") as JsonArray).toStringArray()

								try {
									// Update media info
									mediaModel.updateMediaInfo(media.internalId, filename, name, desc, tags)

									try {
										// Update tags
										scheduleTagsViewRefresh()

										r.success()
									} catch(e: Exception) {
										logger.error("Failed to update tags:")
										e.printStackTrace()
										r.error("Database error")
									}
								} catch(e: Exception) {
									logger.error("Failed to edit file info:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								// Invalid tags JSON array
								r.error(v)
							}
						} else {
							r.error("File does not exist")
						}
					} catch(e: Exception) {
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
		router().post("/api/v1/media/:file/delete").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("files.delete")) {
					val listsModel = ListsModel(r.account())
					val mediaModel = MediaModel(r.account())
					val fileId = r.pathParam("file")

					try {
						val mediaRes = mediaModel.fetchMedia(fileId)

						if(mediaRes.count() > 0) {
							val media = mediaRes.iterator().next()

							// Check if media was created by the user
							if(media.creator != r.userId() && !r.hasPermission("files.delete.all")) {
								r.unauthorized()
								return@launch
							}

							val fs = vertx().fileSystem()

							// Whether this media's files should be deleted
							var delete = false

							// Check if media is a child
							if(media.parent == null) {
								// Check if files with the same hash exist
								val hashMediaRes = mediaModel.fetchMediaByHash(media.hash)

								if(hashMediaRes.count() < 2) {
									delete = true
								}

								try {
									// Fetch children
									val children = mediaModel.fetchMediaChildren(media.internalId)

									// Delete children
									for(child in children) {
										try {
											val file = config.upload_location + child.key

											// Delete file
											if(fs.exists(file).await())
                                                fs.delete(file).await()

											if(child.hasThumbnail) {
												val thumbFile = config.upload_location + "thumbnails/" + child.thumbnailFile

												// Delete thumbnail
												if(fs.exists(thumbFile).await())
                                                    fs.delete(thumbFile).await()
											}

											// Delete entry
											mediaModel.deleteMedia(child.id)

											try {
												// Delete entries linking to the file in lists
												listsModel.deleteListItemsByMediaId(child.internalId)
											} catch(e: Exception) {
												logger.error("Failed to delete list references to child ID ${child.id}:")
												e.printStackTrace()
												r.error("Database error")
												return@launch
											}
										} catch(e: Exception) {
											logger.error("Failed to delete child ID ${child.id}:")
											e.printStackTrace()
											r.error("Database error")
											return@launch
										}
									}
								} catch(e: Exception) {
									logger.error("Failed to fetch media children:")
									e.printStackTrace()
									r.error("Database error")
									return@launch
								}
							} else {
								delete = true
							}

							if(delete) {
								// Delete media files
								try {
									// Delete main file
									val file = media.key

									if(fs.exists(config.upload_location + file).await())
                                        fs.delete(config.upload_location + file).await()
								} catch(e: Exception) {
									// Failed to delete main file
									logger.error("Failed to delete file ${media.key}:")
									e.printStackTrace()
									r.error("Internal error")
									return@launch
								}
								if(media.hasThumbnail) {
									try {
										// Delete thumbnail file
										val file = media.thumbnailFile


										if(fs.exists(config.upload_location + "thumbnails/" + file).await())
                                            fs.delete(config.upload_location + "thumbnails/" + file).await()
									} catch(e: Exception) {
										// Failed to delete thumbnail file
										logger.error("Failed to delete file ${media.thumbnailFile}")
										e.printStackTrace()
										r.error("Internal error")
										return@launch
									}
								}
							}

							try {
								// Delete database entry
								mediaModel.deleteMedia(fileId)

								try {
									// Delete entries linking to the file in lists
									listsModel.deleteListItemsByMediaId(media.internalId)
								} catch(e: Exception) {
									logger.error("Failed to delete list references to media ID $fileId:")
									e.printStackTrace()
									r.error("Database error")
									return@launch
								}

								r.success()
							} catch(e: Exception) {
								logger.error("Failed to delete file entry for ID $fileId:")
								e.printStackTrace()
								r.error("Internal error")
							}
						} else {
							r.error("File does not exist")
						}

						try {
							// Update tags
							scheduleTagsViewRefresh()
						} catch(e: Exception) {
							logger.error("Failed to update tags:")
							e.printStackTrace()
							r.error("Database error")
						}
					} catch(e: Exception) {
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
		//  - offset: (optional) Integer at least 0 that sets the offset of returned results
		//  - limit: (optional) Integer from 0 to 100, sets the amount of results to return
		//  - order: (optional) Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, alphabetically ascending, alphabetically descending, size large to small, size small to large, modified date newest to oldest, modified date oldest to newest)
		// Route params:
		//  - id: String, the alphanumeric ID of the list
		router().get("/api/v1/media/list/:id").virtualHost(hostname).handler { r ->
			val listId = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				val mediaModel = MediaModel()
				val listsModel = ListsModel()
				val accountsModel = AccountsModel()

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
						val listRes = listsModel.fetchList(listId)

						if(listRes.count() > 0) {
							val list = listRes.iterator().next()

							if(list.visibility == ListVisibility.PUBLIC || r.hasPermission("lists.view")) {
								try {
									val account = if(list.type == ListType.AUTOMATICALLY_POPULATED)
										accountsModel.fetchAccountById(list.creator).first()
									else
										null

									// Fetch files based on list type
									val media = when(list.type) {
                                        ListType.AUTOMATICALLY_POPULATED -> {
                                            // Collect source data
                                            val tags = list.sourceTags
                                            val excludeTags = list.sourceExcludeTags
                                            val createdBefore = list.sourceCreatedBefore
                                            val createdAfter = list.sourceCreatedAfter
                                            val mime = list.sourceMime
	                                        val creator = if(account!!.hasPermission("files.list.all") && list.showAllUserFiles)
	                                        	null
	                                        else
	                                        	list.creator

                                            mediaModel.fetchMediaListByTagsDateRangeAndCreator(tags, excludeTags, createdBefore, createdAfter, mime, creator, offset, limit, order)
                                        }
										else -> {
											mediaModel.fetchMediaListByListId(offset, limit, list.internalId, order)
										}
									}

									// Create JSON array of files
									val arr = JsonArray()

									for(file in media)
										arr.add(file.toJson())

									// Send files
									r.success(json {
                                        obj("files" to arr)
                                    })
								} catch(e: Exception) {
									logger.error("Failed to fetch list files:")
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
					} catch(e: Exception) {
						logger.error("Failed to fetch list:")
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