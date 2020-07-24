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
import net.termer.twinemedia.jwt.jwtCreateUnexpiringToken
import net.termer.twinemedia.model.ApiKeysModel
import net.termer.twinemedia.util.*

/**
 * Sets up all API key creation and management routes
 * @since 1.3.0
 */
fun apiKeysController() {
    val domain = appDomain()
    val keysModel = ApiKeysModel()

    // Returns all API keys associated with the user's account
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, name alphabetically ascending, name alphabetically descending)
    get("/api/v1/account/self/keys", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectNonApiKey()) {
                try {
                    // Collect parameters
                    val offset = (if (params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if (params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val order = (if (params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 3)

                    try {
                        // Fetch keys
                        val keys = keysModel.fetchApiKeyList(r.userId(), offset, limit, order)

                        val arr = JsonArray()

                        for (key in keys?.rows.orEmpty())
                            arr.add(key.put("permissions", JsonArray(key.getString("permissions"))))

                        // Send success
                        r.success(json {
                            obj("keys" to arr)
                        })
                    } catch (e: Exception) {
                        logger.error("Failed to fetch API keys:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e: Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Returns info about an API key entry
    // Route parameters:
    //  - id: String, the alphanumeric generated key ID
    get("/api/v1/account/self/key/:id", domain) { r ->
        val id = r.pathParam("id")
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectNonApiKey()) {
                try {
                    // Fetch the key's info
                    val keyRes = keysModel.fetchApiKeyInfo(id)

                    if(keyRes != null && keyRes.rows.size > 0) {
                        val key = keyRes.rows[0]
                        key.put("permissions", JsonArray(key.getString("permissions")))

                        // Send key info
                        r.success(key)
                    } else {
                        r.error("Key does not exist")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to fetch API key entry:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Creates a new API key
    // Parameters:
    //  - name (optional): String with max of 64 characters, the name of the key entry
    //  - permissions: JSON array, the permissions that this API key will be granted
    post("/api/v1/account/self/keys/create", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectNonApiKey()) {
                if(params.contains("name") && params.contains("permissions")) {
                    try {
                        // Collect parameters
                        val name = if (params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                        val permissions = JsonArray(params["permissions"])

                        // Generate ID
                        val id = generateString(10)

                        try {
                            // Generate JWT token
                            val token = jwtCreateUnexpiringToken(json {
                                obj(
                                        "sub" to r.userId(),
                                        "token" to id
                                )
                            })!!

                            try {
                                // Create database entry
                                keysModel.createApiKey(id, name, permissions, token, r.userId())

                                // Send ID and token
                                r.success(json {
                                    obj(
                                            "id" to id,
                                            "token" to token
                                    )
                                })
                            } catch (e: Exception) {
                                logger.error("Failed to create new API key entry:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } catch(e: NullPointerException) {
                            logger.error("Generated JWT token, but function returned null")
                            e.printStackTrace()
                            r.error("Failed to generate token")
                        }
                    } catch(e: Exception) {
                        r.error("Invalid parameters")
                    }
                } else {
                    r.error("Must provide the following values: name, permissions")
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
    post("/api/v1/account/self/key/:id/edit", domain) { r ->
        val id = r.pathParam("id")
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectNonApiKey()) {
                try {
                    // Fetch the key's info
                    val keyRes = keysModel.fetchApiKey(id)

                    if(keyRes != null && keyRes.rows.size > 0) {
                        val key = keyRes.rows[0]

                        try {
                            // Collect parameters
                            val name = if(params.contains("name"))
                                if (params["name"].length > 64) { params["name"].substring(0, 64) } else { params["name"] }
                            else
                                key.getString("key_name")
                            val permissions = if(params.contains("permissions"))
                                JsonArray(params["permissions"])
                            else
                                JsonArray(key.getString("key_permissions"))

                            try {
                                // Update database entry
                                keysModel.updateApiKeyEntry(key.getInteger("id"), name, permissions)

                                // Send success
                                r.success()
                            } catch (e: Exception) {
                                logger.error("Failed to update API key entry:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } catch(e: Exception) {
                            r.error("Invalid parameters")
                        }
                    } else {
                        r.error("Key does not exist")
                    }
                } catch (e: Exception) {
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
    post("/api/v1/account/self/key/:id/delete", domain) { r ->
        val id = r.pathParam("id")
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectNonApiKey()) {
                try {
                    // Fetch the key's info
                    val keyRes = keysModel.fetchApiKey(id)

                    if(keyRes != null && keyRes.rows.size > 0) {
                        val key = keyRes.rows[0]

                        try {
                            keysModel.deleteApiKey(key.getInteger("id"))

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
                } catch (e: Exception) {
                    logger.error("Failed to fetch API key entry:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }
}