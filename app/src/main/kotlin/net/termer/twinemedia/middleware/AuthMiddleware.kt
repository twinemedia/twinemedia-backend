package net.termer.twinemedia.middleware

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.service.TokenService
import net.termer.twinemedia.util.account.AccountContext
import net.termer.twinemedia.util.some

/**
 * Middleware that authenticates requests with a bearer token
 * @since 2.0.0
 */
class AuthMiddleware(
    /**
     * The application config
     */
    private val config: AppConfig
) : Middleware {
    override suspend fun handle(event: RoutingContext) {
        // Skip OPTIONS requests
        if (event.request().method() != HttpMethod.OPTIONS) {
            // Check for a valid bearer token
            val authHeader = event.request().headers()["Authorization"]
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Validate token
                val jwt = authHeader.substring(7)
                val user = TokenService.INSTANCE.authenticateWithJwt(jwt)
                if (user != null) {
                    val principal = user.principal()

                    // Fetch context for token
                    val token: String? = principal.getString("token")
                    val selfAcc = if (token == null) {
                        AccountsModel.INSTANCE.fetchOneSelfDto(
                            AccountsModel.Filters(whereIdIs = some(principal.getString("sub"))),
                            fetchApiKeyInfo = false,
                            config
                        )
                    } else {
                        AccountsModel.INSTANCE.fetchOneSelfDto(
                            AccountsModel.Filters(whereApiKeyIdIs = some(token)),
                            fetchApiKeyInfo = true,
                            config
                        )
                    }

                    // Check if account exists (and API token is valid if the JWT was an API token)
                    if (selfAcc != null) {
                        // All is well; put user and context
                        event.setUser(user)
                        event.put("accountContext", AccountContext(
                            event.vertx(),
                            selfAcc
                        ))
                    }
                }
            }
        }

        event.next()
    }
}
