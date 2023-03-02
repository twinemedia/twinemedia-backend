package net.termer.twinemedia

import io.vertx.ext.web.RoutingContext
import net.termer.twinemedia.Constants.DEFAULT_LANG_CODE

/**
 * Application-wide language class.
 * To access values in it, instantiate it with a language code, or access the global [INSTANCE] property to use one with the language code defined by [DEFAULT_LANG_CODE].
 * @param code The language code to use (case-insensitive)
 * @since 2.0.0
 */
class AppLang(code: String) {
    /**
     * The language code (will be lowercase)
     * @since 2.0.0
     */
    val code: String

    /**
     * The major part of the language code, e.g. the "zh" in "zh-TW" (will be lowercase)
     * @since 2.0.0
     */
    val majorCode: String

    init {
        val lower = code.lowercase()

        this.code = lower
        this.majorCode = lower.split('-')[0]
    }

    companion object {
        val INSTANCE = AppLang(DEFAULT_LANG_CODE)

        /**
         * Creates a [AppLang] instance using the language specified by the request's "Accept-Language" header, or uses [INSTANCE] if no header is present
         * @param ctx The request [RoutingContext]
         * @return The [AppLang] instance
         */
        fun fromRequest(ctx: RoutingContext): AppLang {
            val header = ctx.request().getHeader("accept-language")
            return if (header == null) {
                INSTANCE
            } else {
                AppLang(header.split(',')[0])
            }
        }
    }

    // termer 2023/03/02: There is no support for languages other than English at this time

    /**
     * Credentials are required to update your email and password
     * @since 2.0.0
     */
    fun credentialsRequired() = "Credentials are required to update your email and password"

    /**
     * Invalid credentials
     * @since 2.0.0
     */
    fun invalidCredentials() = "Invalid credentials"

    /**
     * You cannot edit your own account
     * @since 2.0.0
     */
    fun cannotEditSelf() = "You cannot edit your own account"

    /**
     * Rate limited
     * @since 2.0.0
     */
    fun rateLimited() = "Rate limited"
}