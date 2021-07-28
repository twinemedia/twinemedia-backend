package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.jwt.jwtCreateUnexpiringToken
import net.termer.twinemedia.model.ApiKeysModel
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.validation.*
import net.termer.vertx.kotlin.validation.RequestValidator

/**
 * Sets up all API key creation and management routes
 * @since 1.3.0
 */
@DelicateCoroutinesApi
fun apiKeysController() {
	val keysModel = ApiKeysModel()

	for(hostname in appHostnames()) {
		// Returns all API keys associated with the user's account
		// Parameters:
		//  - offset (optional): Integer at least 0 that sets the offset of returned results
		//  - limit (optional): Integer from 0 to 100, sets the amount of results to return
		//  - order (optional): Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, name alphabetically ascending, name alphabetically descending)
		router().get("/api/v1/account/self/keys").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					// Request validation
					val v = RequestValidator()
							.offsetLimitOrder(3)

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int

						try {
							// Fetch keys
							val keys = keysModel.fetchApiKeyList(r.userId(), offset, limit, order)

							// Send success
							r.success(json {obj(
									"keys" to keys.toJsonArray()
							)})
						} catch(e: Exception) {
							logger.error("Failed to fetch API keys:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error("Invalid parameters", v.validationErrorType!!, v.validationErrorText!!)
					}
				}
			}
		}

		// Returns info about an API key entry
		// Route parameters:
		//  - id: String, the alphanumeric generated key ID
		router().get("/api/v1/account/self/key/:id").virtualHost(hostname).handler { r ->
			val id = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					try {
						// Fetch the key's info
						val keyRes = keysModel.fetchApiKeyInfo(id)

						if(keyRes.count() > 0) {
							val key = keyRes.iterator().next()

							// Send key info
							r.success(key.toJson())
						} else {
							r.error("Key does not exist")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch API key entry:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}

		// Creates a new API key
		// Parameters:
		//  - name: String with max of 64 characters, the name of the key entry
		//  - permissions: JSON array, the permissions that this API key will be granted
		router().post("/api/v1/account/self/keys/create").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					// Request validation
					val v = RequestValidator()
							.param("name", Presets.accountNameValidator())
							.param("permissions", PermissionsValidator())

					if(v.validate(r)) {
						// Collect parameters
						val name = v.parsedParam("name") as String
						val permissions = (v.parsedParam("permissions") as JsonArray).toStringArray()

						// Generate ID
						val id = generateString(10)

						try {
							// Generate JWT token
							val token = jwtCreateUnexpiringToken(json {obj(
									"sub" to r.userId(),
									"token" to id
							)})!!

							try {
								// Create database entry
								keysModel.createApiKey(id, name, permissions, token, r.userId())

								// Send ID and token
								r.success(json {obj(
										"id" to id,
										"token" to token
								)})
							} catch(e: Exception) {
								logger.error("Failed to create new API key entry:")
								e.printStackTrace()
								r.error("Database error")
							}
						} catch(e: NullPointerException) {
							logger.error("Generated JWT token, but function returned null")
							e.printStackTrace()
							r.error("Failed to generate token")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Updates an existing API key entry
		// Parameters:
		//  - name (optional): String with max of 64 characters, the new name of the key entry
		//  - permissions (optional): JSON array, the permissions that this API key will be granted
		// Route parameters:
		//  - id: String, the alphanumeric generated key ID
		router().post("/api/v1/account/self/key/:id/edit").virtualHost(hostname).handler { r ->
			val id = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					try {
						// Fetch the key's info
						val keyRes = keysModel.fetchApiKey(id)

						if(keyRes.count() > 0) {
							val key = keyRes.iterator().next()

							// Request validation
							val v = RequestValidator()
									.optionalParam("name", Presets.accountNameValidator(), key.name)
									.optionalParam("permissions", PermissionsValidator(), key.permissions.toJsonArray())

							if(v.validate(r)) {
								// Collect parameters
								val name = v.parsedParam("name") as String
								val permissions = (v.parsedParam("permissions") as JsonArray).toStringArray()

								try {
									// Update database entry
									keysModel.updateApiKeyEntry(key.internalId, name, permissions)

									// Send success
									r.success()
								} catch(e: Exception) {
									logger.error("Failed to update API key entry:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								r.error("Invalid parameters", v.validationErrorType!!, v.validationErrorText!!)
							}
						} else {
							r.error("Key does not exist")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch API key entry:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}

		// Deletes an API key entry
		// Route parameters:
		//  - id: String, the alphanumeric generated key ID
		router().post("/api/v1/account/self/key/:id/delete").virtualHost(hostname).handler { r ->
			val id = r.pathParam("id")
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					try {
						// Fetch the key's info
						val keyRes = keysModel.fetchApiKey(id)

						if(keyRes.count() > 0) {
							val key = keyRes.iterator().next()

							try {
								keysModel.deleteApiKey(key.internalId)

								// Send success
								r.success()
							} catch(e: Exception) {
								logger.error("Failed to delete API key entry:")
								e.printStackTrace()
								r.error("Database error")
							}
						} else {
							r.error("Key does not exist")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch API key entry:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}
	}
}