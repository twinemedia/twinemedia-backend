package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import net.termer.krestx.api.util.ApiResponse
import net.termer.krestx.api.util.apiSuccess
import net.termer.krestx.api.util.apiUnauthorizedError
import net.termer.twinemedia.AppContext
import net.termer.twinemedia.util.account.AccountContext
import net.termer.twinemedia.util.validation.accountContext
import net.termer.twinemedia.util.validation.isAuthenticated

/**
 * Controller for account-related operations
 * @since 2.0.0
 */
class AccountsController(override val appCtx: AppContext, override val ctx: RoutingContext) : Controller() {
    lateinit var accountCtx: AccountContext

    override suspend fun initialize(): ApiResponse? {
        if (!ctx.isAuthenticated())
            return apiUnauthorizedError()

        accountCtx = ctx.accountContext()!!

        return null
    }

    /**
     * Handler for the "getSelfAccount" operation
     * @since 2.0.0
     */
    suspend fun getSelfAccount(): ApiResponse {
        return apiSuccess(accountCtx.selfAccount.toJson())
    }
}
