package net.termer.twinemedia.middleware

import io.vertx.ext.web.RoutingContext

/**
 * A middleware that sets the "ip" property on [RoutingContext] to either the remote host's IP address, or the IP address contained in the provided header, if present
 * @since 2.0.0
 */
class ReverseProxyIpMiddleware(
    /**
     * The reverse proxy IP header, or null to not use any
     */
    private val header: String?
) : Middleware {
    override suspend fun handle(event: RoutingContext) {
        val req = event.request()

        if (header != null && req.headers().contains(header))
            event.put("ip", req.getHeader(header))
        else
            event.put("ip", req.remoteAddress().hostAddress())

        event.next()
    }
}
