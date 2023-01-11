package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.jsonObjectOf
import net.termer.krestx.api.util.ApiResponse
import net.termer.krestx.api.util.apiError
import net.termer.krestx.api.util.apiSuccess
import net.termer.twinemedia.AppContext
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.service.RateLimitService
import net.termer.twinemedia.service.TokenService
import net.termer.twinemedia.util.some
import net.termer.twinemedia.util.validation.apiInvalidCredentialsError

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
    suspend fun postAuth(): ApiResponse {
        // Collect params
        val bodyJson = bodyParams.jsonObject
        val email = bodyJson.getString("email")
        val password = bodyJson.getString("password")

        // Fetch account
        // We use the global AccountsModel here because the request is not yet authenticated, and therefore has no context
        val account = AccountsModel.INSTANCE.fetchOneRow(AccountsModel.Filters(whereEmailIs = some(email)))
            ?: return apiInvalidCredentialsError()

        // Verify password
        if (!CryptoService.INSTANCE.verifyPassword(password, account.hash))
            return apiInvalidCredentialsError()

        // All is well; issue token
        return apiSuccess(jsonObjectOf(
            "token" to TokenService.INSTANCE.createAccountAuthToken(account.id, cfg.jwtExpireMinutes)
        ))
    }
}
