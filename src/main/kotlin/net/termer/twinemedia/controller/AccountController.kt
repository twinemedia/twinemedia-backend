package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.router
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.util.*

/**
 * Sets up all account routes for the user's account
 * @since 1.0
 */
fun accountController() {
	for(hostname in appHostnames()) {
		// Protect all routes in /api/v1/account/
		router().route("/api/v1/account/*").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				try {
					r.authenticate()

					// Pass to next handler only if authenticated
					if(r.protectRoute())
						r.next()
				} catch(e: AuthException) {
					r.unauthorized()
				}
			}
		}

		// Returns all info about the request's account
		router().get("/api/v1/account/info").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				// Check if account exists
				try {
					val perms = JsonArray()
					if(r.account().isApiKey) {
						for(perm in r.account().keyPermissions?.filter { r.account().hasPermission(it) }.orEmpty())
							perms.add(perm)
					} else {
						for(perm in r.account().permissions)
							perms.add(perm)
					}

					// Collect properties
					val account = json {
						obj(
                                "id" to r.account().id,
                                "permissions" to perms,
                                "name" to r.account().name,
                                "email" to r.account().email,
                                "admin" to r.account().hasAdminPermission(),
                                "creation_date" to r.account().creationDate.toISOString(),
                                "exclude_tags" to JsonArray(r.account().excludeTags.asList()),
                                "exclude_other_media" to r.account().excludeOtherMedia,
                                "exclude_other_lists" to r.account().excludeOtherLists,
                                "exclude_other_tags" to r.account().excludeOtherTags,
                                "exclude_other_processes" to r.account().excludeOtherProcesses,
                                "max_upload" to config.max_upload,
                                "api_token" to r.account().isApiKey
                        )
					}

					// Send info in JSON response
					r.success(account)
				} catch(e: AuthException) {
					e.printStackTrace()
					r.error("Internal error")
				}
			}
		}
	}
}