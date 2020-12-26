package net.termer.twinemedia.util

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.impl.MimeMapping
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.ext.auth.authenticateAwait
import net.termer.twine.Twine
import net.termer.twine.utils.RequestUtils
import net.termer.twine.utils.ResponseUtils
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.exception.AuthException
import net.termer.twinemedia.jwt.JWT
import net.termer.twinemedia.model.AccountsModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// Authorization header content regex
private val authPattern: Pattern = Pattern.compile("Bearer (.+)")
private val accountsModel = AccountsModel()

/**
 * Sends an error API response with the provided message
 * @param msg The error message
 * @since 1.0
 */
fun RoutingContext.error(msg: String) {
    response().headers()["Content-Type"] = "application/json"
    response().end(json {
        obj("status" to "error", "error" to msg)
    }.encode())
}

/**
 * Sends a success API response
 * @since 1.0
 */
fun RoutingContext.success() {
    response().headers()["Content-Type"] = "application/json"
    response().end(json {
        obj("status" to "success")
    }.encode())
}

/**
 * Sends a success API response with the provided JSON data
 * @param json The JSON to send along with the success status
 * @since 1.0
 */
fun RoutingContext.success(json: JsonObject) {
    response().headers()["Content-Type"] = "application/json"
    response().end(json.put("status", "success").encode())
}

/**
 * Sends an authorized status and JSON message
 * @since 1.0
 */
fun RoutingContext.unauthorized() {
    response().statusCode = 401
    error("Unauthorized")
}

/**
 * Authenticates this request using its Authentication header. Throws AuthException is header is not present, or if the token is invalid.
 * @exception AuthException If no Authentication header is present, if header is malformed, or if token is invalid
 * @since 1.0
 */
suspend fun RoutingContext.authenticate() {
    // Check for an authorization header
    if(request().headers().contains("Authorization")) {
        val matcher = authPattern.matcher(request().getHeader("Authorization"))

        // Check if header matches pattern
        if(matcher.matches()) {
            try {
                // Set the User object for this RoutingContext
                setUser(JWT.provider?.authenticateAwait(JsonObject().put("jwt", matcher.group(1))))
            } catch (e: Exception) {
                throw AuthException("Invalid JWT token provided")
            }
        } else {
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
 * @since 1.0
 */
fun RoutingContext.authenticated() = user() != null

/**
 * Protects the current request from being accessed without a valid JWT token
 * @return Whether the request is authorized
 * @since 1.0
 */
fun RoutingContext.protectRoute(): Boolean {
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
 * @since 1.0
 */
fun RoutingContext.userId(): Int {

    return user().principal().getInteger("sub")
}

/**
 * Returns the ID of this request's authentication token. Will throw an IllegalStateException if the request is not authenticated.
 * @return The ID of this request's token
 * @since 1.0
 */
fun RoutingContext.tokenId(): String {
    return user().principal().getString("id")
}

/**
 * Returns the account corresponding to this request. Will fail if no valid token is provided with the request
 * @exception AuthException If the current request is not authenticated
 * @return The account corresponding to this request
 * @since 1.0
 */
suspend fun RoutingContext.account(): UserAccount {
    if(authenticated()) {
        if(get("account") as UserAccount? == null) {
            val principle = user().principal()

            // Fetch account from database
            val accountRes = if(principle.containsKey("token")) accountsModel.fetchAccountAndApiKeyByKeyId(principle.getString("token")) else accountsModel.fetchAccountById(userId())

            // Check if account exists
            if (accountRes?.numRows != null && accountRes.numRows > 0) {
                // Set account
                put("account", accountJsonToObject(accountRes.rows[0]))
            } else {
                throw AuthException("This request's token does not have a corresponding account")
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
 * @since 1.0
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
 * @since 1.0
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
 * Publishes a message on this request's event bus channel
 * @param msg The message to send
 * @since 1.0
 */
fun RoutingContext.publish(msg: Any) {
    vertx().eventBus().publish("twinemedia.${tokenId()}", msg)
}

/**
 * Sends a file and respects byte range requests
 * @param path The path to the file to send
 * @since 1.0
 */
fun RoutingContext.sendFileRanged(path: String) {
    if(!response().closed())
        ResponseUtils.sendFileRanged(this, path, Twine.config().getNode("server.static.caching") as Boolean)
}

/**
 * Returns the IP address that this request connected from (respects X-Forwarded-For header if reverse_proxy is enabled)
 * @return The IP address that this request connected from
 * @since 1.0
 */
fun RoutingContext.ip(): String = RequestUtils.resolveIp(request())