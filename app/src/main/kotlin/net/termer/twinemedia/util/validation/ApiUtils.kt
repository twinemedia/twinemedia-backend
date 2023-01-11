package net.termer.twinemedia.util.validation

import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.termer.twinemedia.middleware.ReverseProxyIpMiddleware
import net.termer.twinemedia.util.account.AccountContext

/**
 * Returns the IP address of the client.
 * If [ReverseProxyIpMiddleware] is mounted and configured, the IP from a reverse proxy IP header will be used.
 * @return The IP address of the client
 * @since 2.0.0
 */
inline fun RoutingContext.ip(): String = (this["ip"] ?: this.request().remoteAddress().hostAddress())

/**
 * Allows a header via Access-Control-Allow-Headers
 * @param header The header to allow
 * @return This, to be used fluently
 * @since 1.4.0
 */
fun HttpServerResponse.corsAllowHeader(header: String): HttpServerResponse {
    val allowed = arrayListOf<String>()
    if (headers().contains("Access-Control-Allow-Headers")) {
        val strs = headers()["Access-Control-Allow-Headers"].split(',')

        // Add existing allowed headers
        for (str in strs) {
            val procStr = str.lowercase()
            if (!allowed.contains(procStr))
                allowed.add(procStr)
        }
    }

    // Add new allowed header
    if (!allowed.contains(header.lowercase()))
        allowed.add(header.lowercase())

    // Set Access-Control-Allow-Headers
    putHeader("Access-Control-Allow-Headers", allowed.joinToString(", "))

    return this
}

/**
 * Returns whether this [RoutingContext] is authenticated
 * @return Whether this [RoutingContext] is authenticated
 * @since 2.0.0
 */
fun RoutingContext.isAuthenticated() = this.data().containsKey("accountContext")

/**
 * Returns the [AccountContext] associated with this [RoutingContext], or null if it is not authenticated
 * @return The [AccountContext] associated with this [RoutingContext], or null if it is not authenticated
 * @since 2.0.0
 */
fun RoutingContext.accountContext(): AccountContext? = this.get("accountContext", null)
