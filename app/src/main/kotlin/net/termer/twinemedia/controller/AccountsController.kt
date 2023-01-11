package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import net.termer.krestx.api.util.ApiResponse
import net.termer.krestx.api.util.apiError
import net.termer.krestx.api.util.apiSuccess
import net.termer.krestx.api.util.apiUnauthorizedError
import net.termer.twinemedia.AppContext
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.account.AccountContext
import net.termer.twinemedia.util.validation.accountContext
import net.termer.twinemedia.util.validation.apiInvalidCredentialsError
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

    suspend fun putSelfAccount(): ApiResponse {
        // Collect parameters
        val jsonBody = bodyParams.jsonObject
        val currentPasswordRaw = jsonBody.getString("currentPassword")
        val name = jsonBody.getString("name").orNone()
        val emailRaw = jsonBody.getString("email")
        val passwordRaw = jsonBody.getString("password")
        val excludeTags = jsonBody.getJsonArray("excludeTags")?.toStringArray().orNone()
        val excludeOtherFiles = jsonBody.getBoolean("excludeOtherFiles").orNone()
        val excludeOtherLists = jsonBody.getBoolean("excludeOtherLists").orNone()
        val excludeOtherTags = jsonBody.getBoolean("excludeOtherTags").orNone()
        val excludeOtherProcessPresets = jsonBody.getBoolean("excludeOtherProcessPresets").orNone()
        val excludeOtherSources = jsonBody.getBoolean("excludeOtherSources").orNone()

        var email: Option<String> = none()
        var hash: Option<String> = none()

        // Handle updating email and/or password
        if (emailRaw != null || passwordRaw != null) {
            if (currentPasswordRaw == null) {
                return apiError("credentials_required", "Credentials are required to update your email and password")
            } else {
                // Fetch the user's account and check the password against the user's password hash
                val account = AccountsModel.INSTANCE.fetchOneRow(AccountsModel.Filters(whereInternalIdIs = some(accountCtx.selfAccount.internalId)))
                    ?: return apiUnauthorizedError()

                if (CryptoService.INSTANCE.verifyPassword(currentPasswordRaw, account.hash)) {
                    // The password matches, update value(s)
                    if (emailRaw != null)
                        email = some(emailRaw)
                    if (passwordRaw != null)
                        hash = some(CryptoService.INSTANCE.hashPassword(passwordRaw))
                } else {
                    return apiInvalidCredentialsError()
                }
            }
        }

        // If it got to this point, everything can safely be updated
        AccountsModel.INSTANCE.updateOne(AccountsModel.UpdateValues(
            name = name,
            email = email,
            hash = hash,
            excludeTags = excludeTags,
            excludeOtherFiles = excludeOtherFiles,
            excludeOtherLists = excludeOtherLists,
            excludeOtherTags = excludeOtherTags,
            excludeOtherProcessPresets = excludeOtherProcessPresets,
            excludeOtherSources = excludeOtherSources
        ), AccountsModel.Filters(whereInternalIdIs = some(accountCtx.selfAccount.internalId)))

        // Fetch the user's new self-account and return it
        val selfAccount = if (accountCtx.selfAccount.isApiKey) {
            AccountsModel.INSTANCE.fetchOneSelfDto(
                AccountsModel.Filters(whereApiKeyIdIs = some(accountCtx.selfAccount.keyId!!)),
                fetchApiKeyInfo = true,
                appCtx.config
            )
        } else {
            AccountsModel.INSTANCE.fetchOneSelfDto(
                AccountsModel.Filters(whereInternalIdIs = some(accountCtx.selfAccount.internalId)),
                fetchApiKeyInfo = false,
                appCtx.config
            )
        }

        // This shouldn't happen unless the account or API key was deleted between the beginning of the request and the end of updating values
        if (selfAccount == null)
            return apiUnauthorizedError()

        return apiSuccess(selfAccount.toJson())
    }
}
