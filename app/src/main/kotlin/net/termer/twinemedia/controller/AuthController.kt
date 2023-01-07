package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import net.termer.krestx.api.util.ApiResponse
import net.termer.krestx.api.util.apiSuccess

/**
 * Controller for authentication/authorization operations
 * @since 2.0.0
 */
class AuthController(override val ctx: RoutingContext) : Controller {
    override suspend fun initialize(): ApiResponse? {
        // Nothing to initialize
        return null
    }

    /**
     * Handler for the "auth" operation
     * @since 2.0.0
     */
    suspend fun auth(): ApiResponse {
        return apiSuccess()
    }
}