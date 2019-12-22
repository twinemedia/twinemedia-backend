package net.termer.twinemedia.middleware

import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager
import net.termer.twine.ServerManager.vertx
import net.termer.twine.Twine
import net.termer.twinemedia.Module
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.util.authenticate

/**
 * Automatically authenticates all API routes with JWT if possible
 * @since 1.0
 */
fun authMiddleware() {
    // Handle all /api/ routes on the configured domain
    ServerManager.handler("/api/*", Twine.domains().byName(Module.config.domain).domain()) { r ->
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