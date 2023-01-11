package net.termer.twinemedia.service

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.auth.authentication.TokenCredentials
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.ext.auth.jwt.jwtAuthOptionsOf
import io.vertx.kotlin.ext.auth.jwtOptionsOf
import io.vertx.kotlin.ext.auth.pubSecKeyOptionsOf
import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.util.toBuffer

/**
 * Service for issuing and verifying tokens
 * @since 2.0.0
 */
class TokenService(
    /**
     * The Vert.x instance to use
     */
    vertx: Vertx,

    /**
     * The secret to use for signing JWTs
     */
    jwtSecret: String
) {
    companion object {
        private var _instance: TokenService? = null

        /**
         * The global [TokenService] instance.
         * Will throw [NullPointerException] if accessed before [initInstance] is called.
         * @since 2.0.0
         */
        val INSTANCE
            get() = _instance!!

        /**
         * Initializes the global [INSTANCE] singleton
         * @param vertx The Vert.x instance to use
         * @param config The [AppConfig] to use for the singleton creation
         * @since 2.0.0
         */
        suspend fun initInstance(vertx: Vertx, config: AppConfig) {
            _instance = TokenService(vertx, config.jwtSecret)
        }
    }

    /**
     * The JWT authentication provider used for key signing and verification
     * @since 2.0.0
     */
    val jwtAuth = JWTAuth.create(vertx, jwtAuthOptionsOf(
        pubSecKeys = listOf(
            pubSecKeyOptionsOf(
                algorithm = "HS256",
                buffer = jwtSecret.toBuffer()
            )
        )
    ))

    /**
     * Creates a new JWT, optionally expiring in the specified number of minutes
     * @param claims The JWT claims (body)
     * @param expireMinutes The number of minutes until the token should expire, or <1 to never expire
     * @return The new signed and encoded JWT
     * @since 2.0.0
     */
    fun createJwt(claims: JsonObject, expireMinutes: Int): String {
        return if (expireMinutes > 0)
            jwtAuth.generateToken(claims, jwtOptionsOf(expiresInMinutes = expireMinutes))
        else
            jwtAuth.generateToken(claims)
    }

    /**
     * Creates an authentication token for the account with the specified alphanumeric ID
     * @param accountId The account's alphanumeric ID
     * @param expireMinutes The number of minutes until the token should expire, or <1 to never expire
     * @return The newly created authentication token
     * @since 2.0.0
     */
    fun createAuthToken(accountId: String, expireMinutes: Int): String {
        return createJwt(jsonObjectOf(
            "sub" to accountId
        ), expireMinutes)
    }

    /**
     * Authenticates with a JWT and returns the resulting [User] object, or null if the token was not valid
     * @param jwt The JWT to authenticate with
     * @return The resulting [User] object, or null if the token was not valid
     * @since 2.0.0
     */
    suspend fun authenticateWithJwt(jwt: String): User? {
        return try {
            jwtAuth.authenticate(TokenCredentials(jwt)).await()
        } catch (e: Throwable) {
            null
        }
    }
}
