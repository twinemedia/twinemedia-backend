package net.termer.twinemedia.controller

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.jwt.jwtCreateToken
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.ip
import net.termer.twinemedia.util.success

/**
 * Sets up all authentication-related routes
 * @since 1.0
 */
fun authController() {
	val accountsModel = AccountsModel()

	// Enforce max attempts per minute for authentication
	val attempts = HashMap<String, Int>()
	vertx().setPeriodic(config.auth_timeout_period.toLong()) {
		attempts.clear()
	}

	for(hostname in appHostnames()) {
		// Login route
		// Parameters:
		//  - email: String, the email of the account
		//  - password: String, the password of the account
		router().post("/api/v1/auth").virtualHost(hostname).handler { r ->
			// Check credentials
			val params = r.request().params()

			if(params.contains("email") && params.contains("password")) {
				// Check if max attempts reached, otherwise increment attempts
				if(!attempts.containsKey(r.ip()))
					attempts[r.ip()] = 0
				if(attempts[r.ip()] != null) {
					val count = attempts[r.ip()] ?: 0
					attempts[r.ip()] = count + 1
				}
				if(attempts[r.ip()]!! > config.max_auth_attempts) {
					r.error("Too many attempts, try again later")
					return@handler
				}

				val email = params.get("email")
				val password = params.get("password")

				GlobalScope.launch(vertx().dispatcher()) {
					try {
						// Fetch account info
						val accountRes = accountsModel.fetchAccountByEmail(email)

						// Check if account exists
						if(accountRes != null && accountRes.numRows > 0) {
							val account = accountRes.rows[0]

							// Check if password matches
							val matches = crypt.verifyPassword(password, account["account_hash"])
							if(matches != null && matches) {
								// Generate token ID
								val id = generateString(10)

								// Create JWT token and send it back to the user
								r.success(json {
									obj(
											"token" to jwtCreateToken(json {
												obj("sub" to account["id"], "id" to id)
											})
									)
								})

								// Clear auth attempts
								attempts.remove(r.ip())
							} else {
								r.error("Invalid email or password")
							}
						} else {
							r.error("Invalid email or password")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch account:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			} else {
				r.error("You must include an email and a password field")
			}
		}
	}
}