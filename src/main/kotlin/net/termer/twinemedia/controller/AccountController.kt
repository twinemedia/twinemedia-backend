package net.termer.twinemedia.controller

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.util.*

/**
 * Sets up all account routes for the user's account
 * @since 1.0
 */
fun accountController() {
    val domain = appDomain()

    // Protect all routes in /api/v1/account/
    handler("/api/v1/account/*", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            try {
                r.authenticate()

                // Pass to next handler only if authenticated
                if (r.protectRoute())
                    r.next()
            } catch(e : AuthException) {
                r.unauthorized()
            }
        }
    }

    // Returns all info about the request's account
    get("/api/v1/account/info", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            // Check if account exists
            try {
                // Collect properties
                val account = json {
                    obj(
                            "id" to r.account().getInteger("id"),
                            "permissions" to r.account().getJsonArray("account_permissions"),
                            "name" to r.account().getString("account_name"),
                            "email" to r.account().getString("account_email"),
                            "admin" to r.account().getBoolean("account_admin")
                    )
                }

                // Send info in JSON response
                r.success(account)
            } catch(e : AuthException) {
                e.printStackTrace()
                r.error("Internal error")
            }
        }
    }
}