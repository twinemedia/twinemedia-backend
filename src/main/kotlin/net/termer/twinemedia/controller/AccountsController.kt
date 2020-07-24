package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*

/**
 * Sets up all accounts-related routes for account management
 * @since 1.0
 */
fun accountsController() {
    val domain = appDomain()
    val accountsModel = AccountsModel()

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
                        val accounts = accountsModel.fetchAccountList(offset, limit, order)

                        // Create JSON array of accounts
                        val arr = JsonArray()

                        for(account in accounts?.rows.orEmpty()) {
                            account.put("permissions", JsonArray(account.getString("permissions")))
                            arr.add(account)
                        }

                        // Send accounts
                        r.success(JsonObject().put("accounts", arr))
                    } catch(e: Exception) {
                        logger.error("Failed to fetch accounts:")
                        e.printStackTrace()
                        r.error("Database error")
                    }
                } catch(e: Exception) {
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

                    // Make sure this route cannot be accessed from API keys
                    if(id == r.userId() && r.account().isApiKey) {
                        r.unauthorized()
                        return@launch
                    }

                    try {
                        // Fetch the account
                        val accountRes = accountsModel.fetchAccountInfoById(id)

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
                } catch(e: Exception) {
                    r.error("Invalid parameters")
                }
            }
        }
    }

    // Edits the user's account
    // Parameters:
    //  - name (optional): String with max of 64 characters, the new name of the account
    //  - email (optional): String with max of 64 characters, the new email address of the account
    //  - password (optional): String, the new password for this account
    //  - excludeTags (optional): JSON array, tags to globally exclude when listing files (from searches, lists, or anywhere else an array of files would be returned other than file children)
    //  - excludeOtherMedia (optional): Bool, whether to globally exclude media created by other users when viewing or listing any media
    //  - excludeOtherLists (optional): Bool, whether to globally exclude lists created by other users
    //  - excludeOtherTags (optional): Bool, whether to globally exclude tags added to files created by other users
    //  - excludeOtherProcesses (optional): Bool, whether to globally exclude processes created by other users
    post("/api/v1/account/self/edit", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectNonApiKey()) {
                try {
                    val acc = r.account()

                    // Resolve edit values
                    val name = if (params.contains("name"))
                        if (params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                    else
                        acc.name
                    val email = if (params.contains("email"))
                        if (params["email"].length > 64) params["email"].substring(0, 64) else params["email"]
                    else
                        acc.email
                    val excludeTags = if (params.contains("excludeTags"))
                        JsonArray(params["excludeTags"])
                    else
                        JsonArray(acc.excludeTags.asList())
                    val excludeOtherMedia = if (params.contains("excludeOtherMedia"))
                        params["excludeOtherMedia"]!!.toBoolean()
                    else
                        acc.excludeOtherMedia
                    val excludeOtherLists = if (params.contains("excludeOtherLists"))
                        params["excludeOtherLists"]!!.toBoolean()
                    else
                        acc.excludeOtherLists
                    val excludeOtherTags = if (params.contains("excludeOtherTags"))
                        params["excludeOtherTags"]!!.toBoolean()
                    else
                        acc.excludeOtherTags
                    val excludeOtherProcesses = if (params.contains("excludeOtherProcesses"))
                        params["excludeOtherProcesses"]!!.toBoolean()
                    else
                        acc.excludeOtherTags

                    if (validEmail(email)) {
                        try {
                            var emailExists = false

                            if (email != r.account().email) {
                                // Check if account with that email already exists
                                val emailRes = accountsModel.fetchAccountByEmail(email)

                                emailExists = emailRes != null && emailRes.rows.size > 0
                            }

                            if (emailExists) {
                                r.error("Account with that email already exists")
                            } else {
                                try {
                                    // Hash password if present
                                    val hash = if (params.contains("password")) crypt.hashPassword(params["password"]).orEmpty() else acc.hash

                                    // Update info
                                    accountsModel.updateAccountInfo(acc.id, name, email, hash, excludeTags, excludeOtherMedia, excludeOtherLists, excludeOtherTags, excludeOtherProcesses)

                                    // Success
                                    r.success()
                                } catch (e: Exception) {
                                    logger.error("Failed to update account info:")
                                    e.printStackTrace()
                                    r.error("Database error")
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to fetch account by email:")
                            e.printStackTrace()
                            r.error("Database error")
                        }
                    } else {
                        r.error("Invalid email")
                    }
                } catch(e: Exception) {
                    r.error("Invalid parameters")
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
    //  - name (optional): String with max of 64 characters, the new name of the account
    //  - email (optional): String with max of 64 characters, the new email address of the account
    //  - admin (optional): Bool, whether the account will be an administrator (requires administrator privileges to change)
    //  - permissions (optional): JSON array, the new permissions for the account
    post("/api/v1/account/:id/edit", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("accounts.edit")) {
                try {
                    val id: Int
                    try {
                        id = r.pathParam("id").toInt()
                    } catch(e: Exception) {
                        r.error("Invalid account ID")
                        return@launch
                    }

                    // Make sure this route cannot be accessed from API keys
                    if(id == r.userId() && r.account().isApiKey) {
                        r.unauthorized()
                        return@launch
                    }

                    // Fetch account
                    val accountRes = accountsModel.fetchAccountById(id)

                    // Check if it exists
                    if(accountRes != null && accountRes.rows.size > 0) {
                        // Fetch account info
                        val account = accountJsonToObject(accountRes.rows[0])

                        // Check if editor has permission to edit account
                        if((account.admin && r.account().admin) || !account.admin) {
                            try {
                                // Resolve edit values
                                val name = if(params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                                val email = if(params["email"].length > 64) params["email"].substring(0, 64) else params["email"]
                                val perms = if (params["permissions"] != null) JsonArray(params["permissions"]) else JsonArray(account.permissions.asList())
                                val admin = if (r.account().admin && r.account().id != id) {
                                    if (params["admin"] != null) params["admin"]!!.toBoolean() else account.admin
                                } else {
                                    account.admin
                                }

                                // Make sure non-admin cannot create an admin account
                                if(admin && !r.account().admin) {
                                    r.error("Must be an administrator to make an administrator account")
                                    return@launch
                                }

                                if(validEmail(email)) {
                                    try {
                                        var emailExists = false

                                        if(email != account.email) {
                                            // Check if account with that email already exists
                                            val emailRes = accountsModel.fetchAccountByEmail(email)

                                            emailExists = if (emailRes != null && emailRes.rows.size > 0)
                                                account.id != emailRes.rows[0].getInteger("id")
                                            else
                                                false
                                        }

                                        if (emailExists) {
                                            r.error("Account with that email already exists")
                                        } else {
                                            try {
                                                accountsModel.updateAccountInfo(id, name, email, admin, perms)

                                                r.success()
                                            } catch (e: Exception) {
                                                logger.error("Failed to edit account info:")
                                                e.printStackTrace()
                                                r.error("Database error")
                                            }
                                        }
                                    } catch(e: Exception) {
                                        logger.error("Failed to fetch account by email:")
                                        e.printStackTrace()
                                        r.error("Database error")
                                    }
                                } else {
                                    r.error("Invalid email")
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
                } catch(e: Exception) {
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
    //  - name: String with max of 64 characters, the name of the new account
    //  - email: String with max of 64 characters, the email address of the new account
    //  - admin: Bool, whether the new account will be an administrator
    //  - permissions: JSON array, the permissions the new account will have
    //  - password: String, the password for the new account
    post("/api/v1/accounts/create", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("accounts.create")) {
                // Verify proper parameters were provided
                if(params.contains("name") && params.contains("email") && params.contains("admin") && params.contains("permissions") && params.contains("password")) {
                    try {
                        // Collect and process parameters
                        val name = if(params["name"].length > 64) params["name"].substring(0, 64) else params["name"]
                        val email = if(params["email"].length > 64) params["email"].substring(0, 64) else params["email"]
                        val perms = JsonArray(params["permissions"])
                        val admin = params["admin"]!!.toBoolean()
                        val password = params["password"]

                        // Make sure non-admin cannot create an admin account
                        if(admin && !r.account().admin) {
                            r.error("Must be an administrator to make an administrator account")
                            return@launch
                        }

                        if(validEmail(email)) {
                            // Check if account with same email already exists
                            try {
                                val accountRes = accountsModel.fetchAccountByEmail(email)

                                if (accountRes != null && accountRes.rows.size > 0) {
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
                            } catch (e: Exception) {
                                logger.error("Failed to fetch account:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("Invalid email")
                        }
                    } catch (e: Exception) {
                        // Invalid tags JSON array, or invalid admin value
                        r.error("Invalid parameters")
                        e.printStackTrace()
                    }
                } else {
                    r.error("Must provide the following values: name, email, admin, permissions, password")
                }
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
            if(r.account().admin) {
                try {
                    val id = r.pathParam("id").toInt()

                    // Make sure this route cannot be accessed from API keys
                    if(id == r.userId() && r.account().isApiKey) {
                        r.unauthorized()
                        return@launch
                    }

                    // Stop user from deleting their own account
                    if(id == r.userId()) {
                        r.error("Cannot delete your own account")
                        return@launch
                    }

                    try {
                        // Fetch account
                        val accountRes = accountsModel.fetchAccountById(id)

                        // Check if it exists
                        if(accountRes != null && accountRes.rows.size > 0) {
                            try {
                                // Delete account
                                accountsModel.deleteAccount(id)

                                // Send success
                                r.success()
                            } catch(e: Exception) {
                                logger.error("Failed to delete account:")
                                e.printStackTrace()
                                r.error("Database error")
                            }
                        } else {
                            r.error("Account does not exist")
                        }
                    } catch(e: Exception) {
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