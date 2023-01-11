package net.termer.twinemedia.middleware

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import net.termer.twinemedia.util.validation.corsAllowHeader

/**
 * Middleware that sets up CORS and other headers for API routes
 * @since 2.0.0
 */
class HeadersMiddleware(
    /**
     * The HTTP origin to allow requests from, or "*" to accept from any origin
     */
    private val allowOrigin: String
) : Middleware {
    override suspend fun handle(event: RoutingContext) {
        // Figure out what origin to send in Access-Control-Allow-Origin
        val origin = if(allowOrigin == "*")
            event.request().headers()["Origin"] ?: "*"
        else
            allowOrigin

        // Send headers
        val headers = event.response().headers()
        headers["Content-Type"] = "application/json"
        headers["Accept"] = "application/json"
        headers["Access-Control-Allow-Credentials"] = "true"
        headers["Access-Control-Allow-Origin"] = origin

        // Handle preflight headers
        if(event.request().method() == HttpMethod.OPTIONS)
            event.response()
                .corsAllowHeader("Authorization")
                .corsAllowHeader("Content-Type")

        event.next()
    }
}
