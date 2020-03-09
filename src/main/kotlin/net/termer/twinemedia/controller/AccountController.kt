package net.termer.twinemedia.controller

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.Twine.domains
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.util.account
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.protectRoute
import net.termer.twinemedia.util.success

/**
 * Sets up all account routes for the user's account
 * @since 1.0
 */
fun accountController() {
    val domain = domains().byName(config.domain).domain()

    // Protect all routes in /api/v1/account/
    handler("/api/v1/account/*", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            // Pass to next handler only if authenticated
            if(r.protectRoute())
                r.next()
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