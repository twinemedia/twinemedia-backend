package net.termer.twinemedia.middleware

import io.vertx.core.http.HttpMethod
import net.termer.twine.ServerManager.handler
import net.termer.twine.Twine.domains
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.util.appDomain

/**
 * Sets up CORS and other headers for all requests to /api/
 * @since 1.0
 */
fun headersMiddleware() {
    val domain = appDomain()

    // Handle all /api/ routes on the configured domain
    handler("/api/*", domain) { r ->
        r.response().headers()["Content-Type"] = "application/json"
        r.response().headers()["Accept"] = "application/json, application/x-www-form-urlencoded"
        r.response().headers()["Access-Control-Allow-Credentials"] = "true"
        r.response().headers()["Access-Control-Allow-Origin"] =
                if (config.frontend_host == "*") r.request().getHeader("Origin") else config.frontend_host

        // Handle preflight headers
        if(r.request().method() == HttpMethod.OPTIONS) {
            r.response().headers()["Access-Control-Allow-Headers"] = "authorization"
            r.response().statusCode = 200
            r.response().end()
        }

        // Pass to next handler
        if(!r.response().ended())
            r.next()
    }
}