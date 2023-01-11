package net.termer.twinemedia.util.validation

import io.vertx.ext.web.RoutingContext
import net.termer.twinemedia.middleware.ReverseProxyIpMiddleware

/**
 * Returns the IP address of the client.
 * If [ReverseProxyIpMiddleware] is mounted and configured, the IP from a reverse proxy IP header will be used.
 * @return The IP address of the client
 * @since 2.0.0
 */
inline fun RoutingContext.ip(): String = (this["ip"] ?: this.request().remoteAddress().hostAddress())
