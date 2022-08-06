package net.termer.twinemedia.util

import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.core.json.json as j
import net.termer.twine.Twine
import net.termer.twine.utils.RequestUtils
import net.termer.twine.utils.ResponseUtils
import net.termer.twinemedia.db.dataobject.Account
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.jwt.JWT
import net.termer.twinemedia.model.AccountsModel
import net.termer.vertx.kotlin.validation.RequestValidator
import java.util.regex.Pattern

/**
 * Authorization header content regex
 * @since 1.5.0
 */
val authorizationHeaderPattern: Pattern = Pattern.compile("Bearer (.+)")

// Models
private val accountsModel = AccountsModel()

/**
 * Sends an error API response with the provided message
 * @param msg The error message
 * @since 1.0.0
 */
suspend fun RoutingContext.error(msg: String) {
	json(j {obj(
			"status" to "error", "error" to msg
	)}).await()
}
/**
 * Sends an error API response with the provided message and details
 * @param msg The error message
 * @param details The error details
 * @since 1.4.0
 */
suspend fun RoutingContext.error(msg: String, details: JsonObject) {
	json(j {obj(
			"status" to "error",
			"error" to msg,
			"details" to details
	)}).await()
}
/**
 * Sends an error API response with the provided message and error type and text details
 * @param msg The error message
 * @param type The error type
 * @param text The error plaintext message
 * @since 1.4.0
 */
suspend fun RoutingContext.error(msg: String, type: String, text: String) {
	json(j {obj(
			"status" to "error",
			"error" to msg,
			"details" to j {obj(
					"error_type" to type,
					"error_text" to text
			)}
    )}).await()
}

/**
 * Returns an error based on a failed request validation
 * @param validator The RequestValidator that failed validation
 * @since 1.4.0
 */
suspend fun RoutingContext.error(validator: RequestValidator) {
	json(j {obj(
			"status" to "error",
			"error" to validator.validationErrorText,
			"details" to j {obj(
					"error_type" to validator.validationErrorType,
					"error_text" to validator.validationErrorText,
					"param_name" to validator.validationErrorParam
			)}
		)
	}).await()
}

/**
 * Sends a success API response
 * @since 1.0.0
 */
suspend fun RoutingContext.success() {
	json(j {obj(
			"status" to "success"
	)}).await()
}

/**
 * Sends a success API response with the provided JSON data
 * @param json The JSON to send along with the success status
 * @since 1.0.0
 */
suspend fun RoutingContext.success(json: JsonObject) {
	json(json.put("status", "success")).await()
}

/**
 * Sends an authorized status and JSON message
 * @since 1.0.0
 */
suspend fun RoutingContext.unauthorized() {
	response().statusCode = 401

	if(get("invalidJWT") as Boolean? == true)
		error("Unauthorized", "INVALID_TOKEN", "The provided token is invalid")
	else
		error("Unauthorized")
}

/**
 * Authenticates this request using its Authentication header. Throws AuthException is header is not present, or if the token is invalid.
 * @exception AuthException If no Authentication header is present, if header is malformed, or if token is invalid
 * @since 1.0.0
 */
suspend fun RoutingContext.authenticate() {
	put("invalidJWT", false)

	// Check for an authorization header
	if(request().headers().contains("Authorization")) {
		val matcher = authorizationHeaderPattern.matcher(request().getHeader("Authorization"))

		// Check if header matches pattern
		if(matcher.matches()) {
			try {
				// Set the User object for this RoutingContext
				setUser(JWT.provider?.authenticate(j {obj(
						"token" to matcher.group(1)
				)})?.await())
			} catch (e: Throwable) {
				put("invalidJWT", true)
				throw AuthException("Invalid JWT token provided")
			}
		} else {
			put("invalidJWT", true)
			setUser(null)
			throw AuthException("Invalid authentication header")
		}
	} else {
		setUser(null)
		throw AuthException("No authentication header provided in request")
	}
}

/**
 * Returns whether this request has been authenticated
 * @return If this request has been authenticated
 * @since 1.0.0
 */
fun RoutingContext.authenticated() = user() != null

/**
 * Protects the current request from being accessed without a valid JWT token
 * @return Whether the request is authorized
 * @since 1.0.0
 */
suspend fun RoutingContext.protectRoute(): Boolean {
	if(!authenticated())
		unauthorized()

	return authenticated()
}

/**
 * Protects the current request from being accessed without a valid JWT token or from an API key
 * @return Whether the request is authorized and not from an API key
 * @since 1.3.0
 */
suspend fun RoutingContext.protectNonApiKey(): Boolean {
	return if(authenticated() && !account().isApiKey) true else false.also {
		unauthorized()
	}
}

/**
 * Returns the ID of the user of this request. Will throw an IllegalStateException if the request is not authenticated.
 * @return The ID of this request's user
 * @since 1.0.0
 */
fun RoutingContext.userId(): Int {
	return user().principal().getInteger("sub")
}

/**
 * Returns the ID of this request's authentication token. Will throw an IllegalStateException if the request is not authenticated.
 * @return The ID of this request's token
 * @since 1.0.0
 */
fun RoutingContext.tokenId(): String {
	return user().principal().getString("id")
}

/**
 * Returns the account corresponding to this request. Will fail if no valid token is provided with the request
 * @exception AuthException If the current request is not authenticated
 * @return The account corresponding to this request
 * @since 1.0.0
 */
suspend fun RoutingContext.account(): Account {
	if(authenticated()) {
		if(get("account") as Account? == null) {
			val principal = user().principal()

			// Fetch account from database
			val accountRes = if(principal.containsKey("token"))
				accountsModel.fetchAccountAndApiKeyByKeyId(principal.getString("token")).firstOrNull()
			else
				accountsModel.fetchAccountById(userId()).firstOrNull()

			// Check if account exists
			if (accountRes == null) {
				throw AuthException("This request's token does not have a corresponding account")
			} else {
				// Set account
				put("account", accountRes)
			}
		}
	} else {
		throw AuthException("Cannot fetch account of unauthenticated request")
	}

	return get("account")
}

/**
 * Returns if the user for this request has the specified permission. Will fail if request is not authenticated.
 * @param permission The permission to check
 * @since 1.0.0
 */
suspend fun RoutingContext.hasPermission(permission: String): Boolean {
	var has = false

	if(authenticated()) {
		has = account().hasPermission(permission)
	}

	return has
}

/**
 * Similar to protectRoute(), but requires the provided permission in addition to basic authentication
 * @param permission The permission to check
 * @return Whether the request is authenticated and the user has the specified permission
 * @since 1.0.0
 */
suspend fun RoutingContext.protectWithPermission(permission: String): Boolean {
	var has = false

	if(authenticated() && hasPermission(permission))
		has = true
	else
		unauthorized()

	return has
}

/**
 * Sends a file and respects byte range requests
 * @param path The path to the file to send
 * @since 1.0.0
 */
fun RoutingContext.sendFileRanged(path: String) {
	if(!response().closed())
		ResponseUtils.sendFileRanged(this, path, Twine.config().getNode("server.static.caching") as Boolean)
}

/**
 * Returns the IP address that this request connected from (respects X-Forwarded-For header if Twine is under a reverse proxy)
 * @return The IP address that this request connected from
 * @since 1.0.0
 */
fun RoutingContext.ip(): String = RequestUtils.resolveIp(request())

/**
 * Allows a header via Access-Control-Allow-Headers
 * @param header The header to allow
 * @return This, to be used fluently
 * @since 1.4.0
 */
fun HttpServerResponse.corsAllowHeader(header: String): HttpServerResponse {
	val allowed = arrayListOf<String>()
	if(headers().contains("Access-Control-Allow-Headers")) {
		val strs = headers()["Access-Control-Allow-Headers"].split(',')

		// Add existing allowed headers
		for(str in strs) {
			val procStr = str.lowercase()
			if(!allowed.contains(procStr))
				allowed.add(procStr)
		}
	}

	// Add new allowed header
	if(!allowed.contains(header.lowercase()))
		allowed.add(header.lowercase())

	// Set Access-Control-Allow-Headers
	putHeader("Access-Control-Allow-Headers", allowed.joinToString(", "))

	return this
}