package net.termer.twinemedia.jwt

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config
import java.util.*

/**
 * Object containing global JWT fields
 * @since 1.0.0
 */
object JWT {
	var provider: JWTAuth? = null
}

/**
 * Sets up the JWT keystore and options
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun jwtInit() {
	// Configure provider
	val config = JWTAuthOptions()
			.setPubSecKeys(listOf(
					PubSecKeyOptions()
							.setAlgorithm("HS256")
							.setBuffer(config.jwt_secret)
			))

	// Setup provider
	JWT.provider = JWTAuth.create(vertx(), config)
}

/**
 * Creates a new JWT with the provided data and expiration time
 * @param data The data to store in the token's principle
 * @return The newly generated token
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun jwtCreateToken(data: JsonObject): String? {
	return JWT.provider?.generateToken(data, JWTOptions().setExpiresInMinutes(config.jwt_expire_minutes))
}

/**
 * Creates a new JWT without an expiration date
 * @param data The data to store in the token
 * @return The newly generated token
 * @since 1.3.0
 */
fun jwtCreateUnexpiringToken(data: JsonObject): String? {
	return JWT.provider?.generateToken(data)
}

/**
 * Returns if the provided JWT token is valid and not expired
 * @param jwt The JWT to check
 * @since 1.0.0
 */
suspend fun jwtIsValid(jwt: String): Boolean {
	return try {
		JWT.provider?.authenticate(json {obj(
                "token" to jwt
        )})?.await()
		true
	} catch(e: Exception) {
		false
	}
}

/**
 * Extracts a JWT's principle and returns it
 * @param jwt The JWT to read
 * @return The token's principle
 * @since 1.5.0
 */
fun extractJWTPrinciple(jwt: String): JsonObject {
	return JsonObject(String(Base64.getDecoder().decode(jwt.split('.')[1])))
}