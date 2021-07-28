package net.termer.twinemedia.middleware

import io.vertx.core.http.HttpMethod
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.ServerManager.router
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.corsAllowHeader

/**
 * Sets up CORS and other headers for all requests to /api/
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun headersMiddleware() {
    for(hostname in appHostnames()) {
        // Handle all /api/ routes on the configured domain
        router().route("/api/*").virtualHost(hostname).handler { r ->
            // Figure out what origin to send in Access-Control-Allow-Origin
            val origin = if(config.frontend_host == "*") {
                if(r.request().headers().contains("Origin")) {
                    r.request().getHeader("Origin")
                } else {
                    "*"
                }
            } else {
                config.frontend_host
            }

            // Send headers
            r.response().headers()["Content-Type"] = "application/json"
            r.response().headers()["Accept"] = "application/json, application/x-www-form-urlencoded"
            r.response().headers()["Access-Control-Allow-Credentials"] = "true"
            r.response().headers()["Access-Control-Allow-Origin"] = origin

            // Handle preflight headers
            if(r.request().method() == HttpMethod.OPTIONS)
                r.response()
                        .corsAllowHeader("authorization")
                        .corsAllowHeader("content-type")

            // Pass to next handler
            if(!r.response().ended())
                r.next()
        }
    }
}