package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import net.termer.krestx.api.util.ApiResponse
import net.termer.krestx.api.util.apiSuccess
import net.termer.twinemedia.AppContext
import net.termer.twinemedia.service.RateLimitService

/**
 * Controller for authentication/authorization operations
 * @since 2.0.0
 */
class AuthController(override val appCtx: AppContext, override val ctx: RoutingContext) : Controller() {
    private val cfg = appCtx.config

    override suspend fun initialize(): ApiResponse? {
        // Rate limit auth requests based on config values
        return RateLimitService.INSTANCE.rateLimitRequest(ctx, "AUTH", cfg.authTimeoutSeconds, cfg.authMaxFailedAttempts)
    }

    /**
     * Handler for the "auth" operation
     * @since 2.0.0
     */
    suspend fun auth(): ApiResponse {
        // Collect params
        val bodyJson = bodyParams.jsonObject
        val email = bodyJson.getString("email")
        val password = bodyJson.getString("password")



        return apiSuccess()
    }
}
