package net.termer.twinemedia.middleware

import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.util.*

/**
 * Automatically authenticates all API routes with JWT if possible
 * @since 1.0
 */
fun authMiddleware() {
    val domain = appHostnames()

    // Handle all /api/ routes on the configured domain
    ServerManager.handler("/api/*", domain) { r ->
        GlobalScope.launch(vertx().dispatcher()) {
            // Authenticate route
            try {
                r.authenticate()
            } catch (e: AuthException) { /* Couldn't authenticate */ }

            // Pass to next handler
            r.next()
        }
    }
}