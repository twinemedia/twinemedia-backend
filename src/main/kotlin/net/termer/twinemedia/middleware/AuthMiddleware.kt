package net.termer.twinemedia.middleware

import io.vertx.core.http.HttpMethod
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.router
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.util.*

/**
 * Automatically authenticates all API routes with JWT if possible
 * @since 1.0.0
 */
fun authMiddleware() {
    for(hostname in appHostnames()) {
        // Handle all /api/ routes on the configured domain
        router().route("/api/*").virtualHost(hostname).handler { r ->
            GlobalScope.launch(vertx().dispatcher()) {
                if(r.request().method() == HttpMethod.OPTIONS) {
                    // Skip on OPTIONS
                    r.next()
                } else {
                    // Authenticate route
                    try {
                        r.authenticate()
                    } catch(e: AuthException) { /* Couldn't authenticate */ }

                    // Pass to next handler
                    r.next()
                }
            }
        }
    }
}