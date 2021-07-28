package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.Module.Companion.mediaSourceManager
import net.termer.twinemedia.Module.Companion.taskManager
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.model.ListsModel
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.model.SourcesModel
import net.termer.twinemedia.source.MediaSourceFileNotFoundException
import net.termer.twinemedia.source.StatefulMediaSource
import net.termer.twinemedia.task.Task
import net.termer.twinemedia.util.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Sets up routes to manage media sources
 * @since 1.5.0
 */
@DelicateCoroutinesApi
fun sourcesController() {
	for(hostname in appHostnames()) {
		// Returns all media sources
		// Permissions:
		//  - sources.list
		// Parameters:
		//  - creator (optional): Integer, the creator of sources to return, otherwise returns all
		//  - query (optional): String, the plaintext query to search titles
		//  - offset (optional): Integer at least 0, sets the offset of returned results
		//  - limit (optional): Integer from 0 to 100, sets the amount of results to return
		//  - order (optional): Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, name alphabetically ascending, name alphabetically descending, type alphabetically ascending, type alphabetically descending)
		router().get("/api/v1/sources").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.list")) {
					val sourcesModel = SourcesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.offsetLimitOrder(5)
							.optionalParam("creator", IntValidator())
							.optionalParam("query", StringValidator())

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int
						val creator = v.parsedParam("creator") as Int?
						val query = v.parsedParam("query") as String?

						try {
							// Fetch sources
							val sources = sourcesModel.fetchSourcesByCreatorAndOrPlaintextQuery(creator, query, offset, limit, order)

							// Send sources
							r.success(json {obj(
									"sources" to sources.toJsonArray()
							)})
						} catch(e: Exception) {
							logger.error("Failed to fetch sources:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Returns a media source
		// Permissions:
		//  - sources.view
		// Route parameters:
		//  - id: Integer, the ID of the source
		router().get("/api/v1/source/:id").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.view")) {
					val sourcesModel = SourcesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())

					if(v.validate(r)) {
						// Collect parameters
						val id = v.parsedRouteParam("id") as Int

						try {
							// Fetch source
							val sourceRes = sourcesModel.fetchSourceInfo(id)

							// Check if it exists
							@Suppress("DEPRECATION")
							if(sourceRes.count() > 0) {
								val source = sourceRes.first()

								// Fetch media's source
								val inst = try {
									mediaSourceManager.getSourceInstanceById(source.id)!!
								} catch(e: NullPointerException) {
									logger.error("Tried to fetch media source ID ${source.id}, but it was not registered")
									r.error("Internal error")
									return@launch
								}

								// Get remaining storage space
								val remainingStorage = inst.getRemainingStorage()

								// Fetch type's name
								val sourceType = mediaSourceManager.getAvailableSourceByTypeName(source.type)!!

								// Send source and schema
								r.success(source.toJson()
										.put("type_name", sourceType.name)
										.put("schema", inst.getConfig().getSchema().toJson())
										.put("remaining_storage", remainingStorage)
								)
							} else {
								r.error("Source does not exist")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch source:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Lists all available media source types
		// Permissions:
		//  - sources.create
		router().get("/api/v1/sources/types").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.create")) {
					try {
						val sources = mediaSourceManager.availableSources()
						val arr = JsonArray(ArrayList<Any?>(sources.size))

						// Enumerate all source types
						@Suppress("DEPRECATION")
						for(src in sources) {
							// Create an instance to fetch schema
							val inst = src.sourceClass.newInstance()
							val schema = inst.getConfig().getSchema()

							arr.add(json {obj(
									"type" to src.type,
									"name" to src.name,
									"description" to src.description,
									"schema" to schema.toJson()
							)})
						}

						// Send source types
						r.success(json {obj(
								"types" to arr
						)})
					} catch(e: Exception) {
						logger.error("Failed to enumerate source types and fetch their schemas:")
						e.printStackTrace()
						r.error("Internal error")
					}
				}
			}
		}

		// Returns info about a media source type
		// Permissions:
		//  - sources.create
		// Route parameters:
		//  - type: String, the type's ID
		router().get("/api/v1/sources/type/:type").virtualHost(hostname).handler { r ->
			val type = r.pathParam("type")

			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.create")) {
					try {
						// Fetch source type
						val src = mediaSourceManager.getAvailableSourceByTypeName(type)

						// Check if it exists
						if(src == null) {
							r.error("Invalid type")
							return@launch
						}

						// Create an instance to fetch schema
						@Suppress("DEPRECATION")
						val inst = src.sourceClass.newInstance()
						val schema = inst.getConfig().getSchema()

						// Send source type
						r.success(json {obj(
								"type" to src.type,
								"name" to src.name,
								"description" to src.description,
								"schema" to schema.toJson()
						)})
					} catch(e: Exception) {
						logger.error("Failed fetch source and its schema:")
						e.printStackTrace()
						r.error("Internal error")
					}
				}
			}
		}

		// Creates a new media source
		// Permissions:
		//  - sources.create
		// Parameters:
		//  - type: String, the ID of the type of media source that will be created
		//  - name: String, the new media source's name
		//  - config: JSON object, the new media source's configuration (must follow the chosen type's schema)
		//  - global: Bool, whether the new media source will be visible and usable by all accounts, regardless of permission
		//  - test (optional): Bool, whether to test the newly created media source by checking if a non-existent file exists
		router().post("/api/v1/sources/create").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.create")) {
					val sourcesModel = SourcesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.param("type", StringValidator())
							.param("name", StringValidator()
									.minLength(1)
									.maxLength(256))
							.param("config", JsonValidator())
							.param("global", BooleanValidator())
							.optionalParam("test", BooleanValidator(), false)

					if(v.validate(r)) {
						// Collect parameters
						val type = v.parsedParam("type") as String
						val name = v.parsedParam("name") as String
						val config = v.parsedParam("config") as JsonObject
						val global = v.parsedParam("global") as Boolean
						val test = v.parsedParam("test") as Boolean

						// Fetch source type
						val srcType = mediaSourceManager.getAvailableSourceByTypeName(type)

						// Check if it exists
						if(srcType == null) {
							r.error("Invalid type")
							return@launch
						}

						// Create an instance to fetch schema
						val srcInst = try {
							@Suppress("DEPRECATION")
							srcType.sourceClass.newInstance()
						} catch(e: Exception) {
							logger.error("Failed to create instance of media source type \"$type\":")
							e.printStackTrace()
							r.error("Internal error")
							return@launch
						}
						val schema = srcInst.getConfig().getSchema()

						// Validate config against schema
						val valRes = schema.validate(config)
						if(!valRes.valid) {
							r.error(valRes.errorText!!, valRes.errorType!!, valRes.errorText)
							return@launch
						}

						// Try testing the entry if "test" is true
						if(test) {
							try {
								// Configure instance created earlier for testing
								srcInst.getConfig().configure(config)

								// Start if it's a StatefulMediaSource
								if(srcInst is StatefulMediaSource)
									srcInst.startup()

								// Try fetching info on a file that (shouldn't) exist
								try {
									srcInst.getFile(generateString(10))

									// All is well if nothing bad happened by this point
								} catch(e: MediaSourceFileNotFoundException) {
									// All is well because the key could not be found, but no other exception was thrown
								}
							} catch(e: Exception) {
								logger.error("Testing source failed:")
								e.printStackTrace()
								r.error("Testing media source failed, your configuration may have errors")
								return@launch
							}
						}

						try {
							// Create source entry and register it
							val id = sourcesModel.createSource(type, name, config, r.account().id, global)
							mediaSourceManager.registerInstance(id, srcType.sourceClass, config)

							// Send success
							r.success(json {obj(
									"id" to id
							)})
						} catch(e: Exception) {
							logger.error("Failed to create source:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Deletes a media source.
		// Note that all media file *entries* with the media source will be deleted, but the underlying files will not.
		// If files need to be deleted, they should be deleted in the normal way individually.
		// Permissions:
		//  - sources.delete
		// Route parameters:
		//  - id: Int, the ID of the media source to delete
		// Parameters:
		//  - forceDelete (optional): Bool, whether to force-delete the source even if it is currently in use
		//  - deleteContents (optional): Bool, whether to delete all files with media entries in the source
		router().post("/api/v1/source/:id/delete").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.delete")) {
					val sourcesModel = SourcesModel(r.account())
					val mediaModel = MediaModel()
					val listsModel = ListsModel()
					val accountsModel = AccountsModel()

					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())
							.optionalParam("forceDelete", BooleanValidator(), false)
							.optionalParam("deleteContents", BooleanValidator(), false)

					if(v.validate(r)) {
						// Collect parameters
						val id = v.parsedRouteParam("id") as Int
						val forceDelete = v.parsedParam("forceDelete") as Boolean
						val deleteContents = v.parsedParam("deleteContents") as Boolean

						try {
							// Fetch source
							val sourceRes = sourcesModel.fetchSource(id)

							// Check if it exists
							if(sourceRes.rowCount() < 1) {
								r.error("Invalid ID")
								return@launch
							}

							val source = sourceRes.first()

							// Fetch source instance
							val inst = mediaSourceManager.getSourceInstanceById(id)

							// Perform actions on the instance if it exists
							if(inst != null) {
								// Check if any locks are present
								if(inst.getLock().locked() && !forceDelete) {
									r.error("Source is currently in use and cannot be gracefully deleted")
									return@launch
								}

								// If no locks are present and the source isn't still needed, delete the instance
								try {
									if(!deleteContents)
										mediaSourceManager.deleteInstance(id)
								} catch(e: Exception) {
									logger.error("Failed to shutdown StatefulMediaSource with ID $id:")
									e.printStackTrace()
									r.error("Internal error")
									return@launch
								}
							} else if(deleteContents) {
								r.error("Source instance could not be found, so source contents cannot be deleted")
								return@launch
							}

							// Delete source if not being used
							if(!deleteContents)
								sourcesModel.deleteSource(id)

							// Update default sources to be -1 (invalid) on accounts using this as their default source
							accountsModel.updateAccountDefaultSourceByDefaultSource(id, -1)

							// Delete media entries with the source if not deleting contents, otherwise create a task to delete each file, and return its ID
							if(deleteContents) {
								var cancelled = false

								// Create deletion task
								val task = taskManager.createTask(
										name = "Deleting Source Contents of \"${source.name}\"",
										isGlobal = false,
										progressType = Task.ProgressType.ITEM_COUNT,
										initialSubjects = arrayOf(r.account().id),
										cancelRequestHandler = { cancelled = true }
								)

								GlobalScope.launch(vertx().dispatcher()) {
									try {
										val fs = vertx().fileSystem()

										task.subtask = "Collecting items"

										val total = mediaModel.fetchMediaCountBySource(id)
										task.totalItems = total

										task.subtask = "Deleting items"

										var finished = 0
										task.finishedItems = 0
										while(!cancelled && finished < total) {
											// Fetch next batch of items
											val batch = mediaModel.fetchMediaBySource(id, task.finishedItems, 100, 0)

											for(item in batch) {
												if(cancelled)
													break

												// Delete file from storage
												try {
													inst!!.deleteFile(item.key)
												} catch(e: MediaSourceFileNotFoundException) {}

												// Delete its thumbnail if present
												if(item.hasThumbnail) {
													val thumbPath = config.thumbnails_location + item.thumbnailFile

													if(fs.exists(thumbPath).await())
														fs.delete(thumbPath).await()
												}

												// Delete entry
												mediaModel.deleteMedia(item.id)

												// Delete list items that reference it
												listsModel.deleteListItemsByMediaId(item.internalId)

												// Increment finished items
												finished++
												task.finishedItems = finished
											}
										}

										if(cancelled) {
											task.cancel()
										} else {
											task.subtask = "Deleting source"

											sourcesModel.deleteSource(id)
											mediaSourceManager.deleteInstance(id)

											task.succeed()
										}
									} catch(e: Exception) {
										task.fail(e)
									}
								}

								// Return task ID
								r.success(json {obj(
										"task" to task.id
								)})
							} else {
								mediaModel.deleteMediaBySource(id)

								// Send success
								r.success()
							}
						} catch(e: Exception) {
							logger.error("Failed to delete source:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Edits an existing media source
		// Permissions:
		//  - sources.edit
		// Route parameters:
		//  - id: Int, the ID of the source to edit
		// Parameters:
		//  - name (optional): String, the media source's new name
		//  - config (optional): JSON object, the media source's new configuration (must follow the source type's schema)
		//  - global (optional): Bool, whether the media source will be visible and usable by all accounts, regardless of permission
		//  - creator (optional): Integer, the media source's new creator's ID
		//  - test (optional): Bool, whether to test the media source's new configuration by checking if a non-existent file exists (only has an effect if config is provided)
		//  - forceEdit (optional): Bool, whether to force-edit the source even if it is currently in use (only has an effect if config is provided)
		router().post("/api/v1/source/:id/edit").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("sources.create")) {
					val sourcesModel = SourcesModel(r.account())
					val accountsModel = AccountsModel()

					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())
							.optionalParam("name", StringValidator()
									.minLength(1)
									.maxLength(256))
							.optionalParam("config", JsonValidator())
							.optionalParam("global", BooleanValidator())
							.optionalParam("creator", IntValidator())
							.optionalParam("test", BooleanValidator(), false)
							.optionalParam("forceEdit", BooleanValidator(), false)

					if(v.validate(r)) {
						// Collect parameters
						val id = v.parsedRouteParam("id") as Int
						val name = v.parsedParam("name") as String?
						val config = v.parsedParam("config") as JsonObject?
						val global = v.parsedParam("global") as Boolean?
						val creator = v.parsedParam("creator") as Int?
						val test = v.parsedParam("test") as Boolean
						val forceEdit = v.parsedParam("forceEdit") as Boolean

						// Fetch source
						val sourceRes = try {
							sourcesModel.fetchSource(id)
						} catch(e: Exception) {
							logger.error("Failed to fetch source ID $id:")
							e.printStackTrace()
							r.error("Database error")
							return@launch
						}
						if(sourceRes.rowCount() < 1) {
							r.error("Invalid ID")
							return@launch
						}
						val source = sourceRes.first()

						// Fetch its instance
						val inst = try {
							mediaSourceManager.getSourceInstanceById(id)!!
						} catch(e: NullPointerException) {
							logger.error("Tried to fetch media source ID $id, but it was not registered")
							r.error("Internal error")
							return@launch
						}

						// Fetch source type
						val srcType = try {
							mediaSourceManager.getAvailableSourceByTypeName(source.type)!!
						} catch(e: NullPointerException) {
							logger.error("Tried to fetch info for source type ${source.type}, but it was not registered:")
							e.printStackTrace()
							r.error("Internal error")
							return@launch
						}

						// Make sure account exists if provided
						if(creator != null) {
							val accountRes = try {
								accountsModel.fetchAccountById(creator)
							} catch(e: Exception) {
								logger.error("Failed to fetch account ID $creator:")
								e.printStackTrace()
								r.error("Database error")
								return@launch
							}
							if(accountRes.rowCount() < 1) {
								r.error("Invalid account")
								return@launch
							}
						}

						// Validate config against schema if provided
						if(config != null) {
							val schema = inst.getConfig().getSchema()

							// Validate config against schema
							val valRes = schema.validate(config)
							if(!valRes.valid) {
								r.error(valRes.errorText!!, valRes.errorType!!, valRes.errorText)
								return@launch
							}

							// Check if any locks are in place
							if(inst.getLock().locked() && !forceEdit) {
								r.error("Source is currently in use and cannot be gracefully reloaded")
								return@launch
							}

							// Try testing the entry if "test" is true
							if(test) {
								try {
									// Create an instance for testing
									val srcInst = try {
										@Suppress("DEPRECATION")
										srcType.sourceClass.newInstance()
									} catch(e: Exception) {
										logger.error("Failed to create instance of media source type \"${source.type}\":")
										e.printStackTrace()
										r.error("Internal error")
										return@launch
									}

									// Configure instance created for testing
									srcInst.getConfig().configure(config)

									// Start if it's a StatefulMediaSource
									if(srcInst is StatefulMediaSource)
										srcInst.startup()

									// Try fetching info on a file that (shouldn't) exist
									try {
										srcInst.getFile(generateString(10))

										// All is well if nothing bad happened by this point
									} catch(e: MediaSourceFileNotFoundException) {
										// All is well because the key could not be found, but no other exception was thrown
									}
								} catch(e: Exception) {
									logger.error("Testing source failed:")
									e.printStackTrace()
									r.error("Testing media source failed, your configuration may have errors")
									return@launch
								}
							}
						}

						try {
							// Update source entry
							sourcesModel.updateSourceInfo(id, name ?: source.name, global ?: source.global, config ?: source.config, creator ?: source.creator)
						} catch(e: Exception) {
							logger.error("Failed to create source:")
							e.printStackTrace()
							r.error("Database error")
						}

						// Reload source if a new config was provided
						if(config != null) {
							try {
								mediaSourceManager.deleteInstance(id)
								mediaSourceManager.registerInstance(id, srcType.sourceClass, config)
							} catch(e: Exception) {
								logger.error("Failed to reload source $id:")
								e.printStackTrace()
								r.error("Internal error")
								return@launch
							}
						}

						// Send success
						r.success(json {obj(
								"id" to id
						)})
					} else {
						r.error(v)
					}
				}
			}
		}
	}
}