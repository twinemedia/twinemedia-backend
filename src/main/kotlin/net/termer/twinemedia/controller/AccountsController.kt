package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.Twine.domains
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*

/**
 * Sets up all accounts-related routes for account management
 * @since 1.0
 */
fun accountsController() {
    val domain = domains().byName(config.domain).domain()

    // Returns a list of accounts
    // Permissions:
    //  - accounts.list
    // Parameters:
    //  - offset: Integer at least 0 that sets the offset of returned results
    //  - limit: Integer from 0 to 100, sets the amount of results to return
    //  - order: Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, name alphabetically ascending, name alphabetically descending, email alphabetically ascending, email alphabetically descending)
    get("/api/v1/accounts", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("accounts.list")) {
                try {
                    // Collect parameters
                    val offset = (if(params.contains("offset")) params["offset"].toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params["limit"].toInt() else 100).coerceIn(0, 100)
                    val order = (if(params.contains("order")) params["order"].toInt() else 0).coerceIn(0, 5)

                    try {
                        // Fetch accounts
                        val accounts = fetchAccountList(offset, limit, order)

                        // Create JSON array of accounts
                        val arr = JsonArray()

                        for(account in accounts?.rows.orEmpty()) {
                            account.put("permissions", JsonArray(account.getString("permissions")))
                            arr.add(account)
                        }

                        // Send accounts
                        r.success(JsonObject().put("accounts", arr))
                    } catch(e : Exception) {
                        logger.error("Failed to fetch accounts:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Returns info about an account
    // Permissions:
    //  - accounts.view
    // Route parameters:
    //  - id: Integer, the ID of the account
    get("/api/v1/account/:id", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("accounts.view")) {
                try {
                    // Fetch the account ID as Int
                    val id = r.pathParam("id").toInt()

                    try {
                        // Fetch the account
                        val accountRes = fetchAccountInfoById(id)

                        if(accountRes != null && accountRes.rows.size > 0) {
                            val account = accountRes.rows[0]
                            account.put("permissions", JsonArray(account.getString("permissions")))

                            // Send account
                            r.success(account)
                        } else {
                            r.error("Account does not exist")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to fetch account info:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e : Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Edits the user's account
    // Parameters:
    //  - name (optional): String, the new name of the account
    //  - email (optional): String, the new email address of the account
    //  - password (optional): String, the new password for this account
    post("/api/v1/account/self/edit", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectRoute()) {
                try {
                    val acc = r.account()

                    // Resolve edit values
                    val name = if (params.contains("name")) {
                        if (params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                    } else {
                        acc.getString("account_name")
                    }
                    val email = if (params.contains("email")) {
                        if (params["email"].length > 64) params["email"].substring(0, 64) else params["email"]
                    } else {
                        acc.getString("account_email")
                    }

                    // Hash password if present
                    val hash = if (params.contains("password")) crypt.hashPassword(params["password"]).orEmpty() else acc.getString("account_hash")

                    // Update info
                    updateAccountInfo(acc.getInteger("id"), name, email, hash)

                    // Success
                    r.success()
                } catch(e : Exception) {
                    logger.error("Failed to update account info:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Edits an account's info and permissions
    // Permissions:
    //  - accounts.edit
    //  - Administrator privileges (only required if changing an account's administrator status, or if the account being edited is an administrator)
    // Route parameters:
    //  - id: Integer, the ID of the account to edit
    // Parameters:
    //  - name (optional): String, the new name of the account
    //  - email (optional): String, the new email address of the account
    //  - admin (optional): Bool, whether the account will be an administrator (requires administrator privileges to change)
    //  - permissions (optional): JSON array, the new permissions for the account
    post("/api/v1/account/:id/edit", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("accounts.edit")) {
                try {
                    var id = -1
                    try {
                        id = r.pathParam("id").toInt()
                    } catch(e : Exception) {
                        r.error("Invalid account ID")
                        return@launch
                    }

                    // Fetch account
                    val accountRes = fetchAccountInfoById(id)

                    // Check if it exists
                    if(accountRes != null && accountRes.rows.size > 0) {
                        // Fetch account info
                        val account = accountRes.rows[0]

                        // Check if editor has permission to edit account
                        if((account.getBoolean("admin") && r.account().getBoolean("account_admin")) || !account.getBoolean("admin")) {
                            try {
                                // Resolve edit values
                                val name = if(params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                                val email = if(params["email"].length > 64) params["email"].substring(0, 64) else params["email"]
                                val perms = if (params["permissions"] != null) JsonArray(params["permissions"]) else JsonArray(account.getString("permissions"))
                                val admin = if (r.account().getBoolean("account_admin") && r.account().getInteger("id") != id) {
                                    if (params["admin"] != null) params["admin"].toBoolean() else account.getBoolean("admin")
                                } else {
                                    account.getBoolean("admin")
                                }

                                try {
                                    updateAccountInfo(id, name, email, admin, perms)

                                    r.success()
                                } catch (e: Exception) {
                                    logger.error("Failed to edit account info:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
                            } catch (e: Exception) {
                                // Invalid tags JSON array, or invalid admin value
                                r.error("Invalid parameters")
                            }
                        } else {
                            r.error("Only administrators may edit administrator accounts")
                        }
                    } else {
                        r.error("Account does not exist")
                    }
                } catch(e : Exception) {
                    logger.error("Failed to fetch account:")
                    e.printStackTrace()
                    r.error("Database error")
                }
            }
        }
    }

    // Creates a new account
    // Permissions:
    //  - Administrator privileges
    // Parameters:
    //  - name: String, the name of the new account
    //  - email: String, the email address of the new account
    //  - admin: Bool, whether the new account will be an administrator
    //  - permissions: JSON array, the permissions the new account will have
    //  - password: String, the password for the new account
    post("/api/v1/accounts/create", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.account().getBoolean("account_admin")) {
                // Verify proper parameters were provided
                if(params.contains("name") && params.contains("email") && params.contains("admin") && params.contains("permissions") && params.contains("password")) {
                    try {
                        // Collect and process parameters
                        val name = if(params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                        val email = if(params["email"].length > 64) params["email"].substring(0, 64) else params["email"]
                        val perms = JsonArray(params["permissions"])
                        val admin = params["admin"].toBoolean()
                        val password = params["password"]

                        // Check if account with same email already exists
                        try {
                            val accountRes = fetchAccountByEmail(email)

                            if(accountRes != null && accountRes.rows.size > 0) {
                                r.error("Account with that email already exists")
                            } else {
                                try {
                                    // Create account
                                    createAccount(name, email, admin, perms, password)

                                    // Send success
                                    r.success()
                                } catch (e: Exception) {
                                    logger.error("Failed to create new account:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
                            }
                        } catch(e : Exception) {
                            logger.error("Failed to fetch account:")
                            e.printStackTrace()
                            r.error("Database error")
                        }
                    } catch (e: Exception) {
                        // Invalid tags JSON array, or invalid admin value
                        r.error("Invalid parameters")
                        e.printStackTrace()
                    }
                } else {
                    r.error("Must provide the following values: name, email, admin, permissions, password")
                }
            } else {
                r.unauthorized()
            }
        }
    }

    // Deletes an account
    // Permissions:
    //  - Administrator privileges
    // Route parameters:
    //  - id: Integer, the ID of the account to delete
    post("/api/v1/account/:id/delete", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.account().getBoolean(("account_admin"))) {
                try {
                    val id = r.pathParam("id").toInt()

                    try {
                        // Fetch account
                        val accountRes = fetchAccountById(id)

                        // Check if it exists
                        if(accountRes != null && accountRes.rows.size > 0) {
                            try {
                                // Delete account
                                deleteAccount(id)

                                // Send success
                                r.success()
                            } catch(e : Exception) {
                                logger.error("Failed to delete account:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("Account does not exist")
                        }
                    } catch(e : Exception) {
                        logger.error("Failed to fetch account:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch (e: Exception) {
                    r.error("Invalid parameters")
                }
            } else {
                r.unauthorized()
            }
        }
    }
}