package net.termer.twinemedia.jwt

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.jwt.JWTOptions
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.ext.auth.KeyStoreOptions
import io.vertx.kotlin.ext.auth.authenticateAwait
import io.vertx.kotlin.ext.auth.jwt.JWTAuthOptions
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config

/**
 * Object containing global JWT fields
 * @since 1.0
 */
object JWT {
    var provider: JWTAuth? = null
}

/**
 * Sets up the JWT keystore and options
 * @since 1.0
 */
fun jwtInit() {
    // Configure provider
    var config = JWTAuthOptions(
            keyStore = KeyStoreOptions(
                    path = config.keystore_path,
                    password = config.keystore_secret
            )
    )

    // Setup provider
    JWT.provider = JWTAuth.create(vertx(), config)
}

/**
 * Creates a new JWT token wit the provided data and expiration time
 * @param data The data to store in the token
 * @return The newly generated token
 * @since 1.0
 */
fun jwtCreateToken(data : JsonObject) : String? {
    return JWT.provider?.generateToken(data, JWTOptions().setExpiresInMinutes(config.keystore_expire_minutes))
}

/**
 * Returns if the provided JWT token is valid and not expired
 * @param jwt The JWT token to check
 * @since 1.0
 */
suspend fun jwtIsValid(jwt : String) : Boolean {
    return try {
        JWT.provider?.authenticateAwait(json {
            obj("jwt" to jwt)
        })
        true
    } catch(e : Exception) {
        false
    }
}

/**
 * Returns the data stored in the provided JWT token
 * @param jwt The JWT token to read
 * @since 1.0
 */
suspend fun jwtData(jwt : String) : JsonObject? {
    return JWT.provider?.authenticateAwait(json {
        obj("jwt" to jwt)
    })?.principal()
}