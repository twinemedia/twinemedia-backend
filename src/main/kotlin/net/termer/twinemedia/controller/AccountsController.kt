package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.json.schema.common.dsl.Schemas.*
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.*
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.validation.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Sets up all accounts-related routes for account management
 * @since 1.0.0
 */
fun accountsController() {
	val accountsModel = AccountsModel()

	for(hostname in appHostnames()) {
		// Returns a list of accounts
		// Permissions:
		//  - accounts.list
		// Parameters:
		//  - offset (optional): Integer at least 0 that sets the offset of returned results
		//  - limit (optional): Integer from 0 to 100, sets the amount of results to return
		//  - order (optional): Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, name alphabetically ascending, name alphabetically descending, email alphabetically ascending, email alphabetically descending)
		router().get("/api/v1/accounts").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.list")) {
					// Request validation
					val v = RequestValidator()
							.optionalParam("offset", Presets.resultOffsetValidator(),0)
							.optionalParam("limit", Presets.resultLimitValidator(), 100)
							.optionalParam("order",
									IntValidator()
											.coerceMin(0)
											.coerceMax(5),
									0)

					if(v.validate(r)) {
						// Collect parameters
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int

						try {
							// Fetch accounts
							val accounts = accountsModel.fetchAccountList(offset, limit, order)

							// Create JSON array of accounts
							val arr = JsonArray()

							for(account in accounts)
								arr.add(account.toJson())

							// Send accounts
							r.success(JsonObject().put("accounts", arr))
						} catch(e: Exception) {
							logger.error("Failed to fetch accounts:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Returns info about an account
		// Permissions:
		//  - accounts.view
		// Route parameters:
		//  - id: Integer, the ID of the account
		router().get("/api/v1/account/:id").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.view")) {
					// Request validation
					val v = RequestValidator()
							.routeParam("id",
									IntValidator()
											.coerceMin(0))

					if(v.validate(r)) {
						// Fetch the account ID as Int
						val id = v.parsedRouteParam("id") as Int

						// Make sure this route cannot be accessed from API keys
						if(id == r.userId() && r.account().isApiKey) {
							r.unauthorized()
							return@launch
						}

						try {
							// Fetch the account
							val account = accountsModel.fetchAccountInfoById(id).firstOrNull()

							if(account != null) {
								// Send account
								r.success(account.toJson())
							} else {
								r.error("Account does not exist")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch account info:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Edits the user's account
		// Parameters:
		//  - name (optional): String with max of 64 characters, the new name of the account
		//  - email (optional): String with max of 64 characters, the new email address of the account
		//  - password (optional): String, the new password for this account
		//  - excludeTags (optional): JSON array, tags to globally exclude when listing files (from searches, lists, or anywhere else an array of files would be returned other than file children)
		//  - excludeOtherMedia (optional): Bool, whether to globally exclude media created by other users when viewing or listing any media
		//  - excludeOtherLists (optional): Bool, whether to globally exclude lists created by other users
		//  - excludeOtherTags (optional): Bool, whether to globally exclude tags added to files created by other users
		//  - excludeOtherProcesses (optional): Bool, whether to globally exclude processes created by other users
		router().post("/api/v1/account/self/edit").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					val acc = r.account()

					// Request validation
					val v = RequestValidator()
							.param("currentPassword", StringValidator()
									.maxLength(64)
									.noNewlinesOrControlChars())
							.optionalParam("name", Presets.accountNameValidator(), acc.name)
							.optionalParam("email", EmailValidator().trim(), acc.email)
							.optionalParam("excludeTags", TagsValidator(), acc.excludeTags.toJsonArray())
							.optionalParam("excludeOtherMedia", BooleanValidator(), acc.excludeOtherMedia)
							.optionalParam("excludeOtherLists", BooleanValidator(), acc.excludeOtherLists)
							.optionalParam("excludeOtherTags", BooleanValidator(), acc.excludeOtherTags)
							.optionalParam("excludeOtherProcesses", BooleanValidator(), acc.excludeOtherProcesses)
							.optionalParam("password", PasswordValidator())

					if(v.validate(r)) {
						// Get edit values
						val currentPassword = v.parsedParam("currentPassword") as String
						val name = v.parsedParam("name") as String
						val email = v.parsedParam("email") as String
						val excludeTags = (v.parsedParam("excludeTags") as JsonArray).toStringArray()
						val excludeOtherMedia = v.parsedParam("excludeOtherMedia") as Boolean
						val excludeOtherLists = v.parsedParam("excludeOtherLists") as Boolean
						val excludeOtherTags = v.parsedParam("excludeOtherTags") as Boolean
						val excludeOtherProcesses = v.parsedParam("excludeOtherProcesses") as Boolean
						val password = v.parsedParam("password") as String?

						try {
							// Validate password
							if(!crypt.verifyPassword(currentPassword, acc.hash)!!) {
								r.error("Password does not match existing password")
								return@launch
							}

							var emailExists = false
							if(email != r.account().email) {
								// Check if account with that email already exists
								val emailRes = accountsModel.fetchAccountByEmail(email)

								emailExists = emailRes.count() > 0
							}

							if(emailExists) {
								r.error("Account with that email already exists")
							} else {
								try {
									val hash = if(password == null) acc.hash else crypt.hashPassword(password)!!

									// Update info
									accountsModel.updateAccountInfo(acc.id, name, email, hash, excludeTags, excludeOtherMedia, excludeOtherLists, excludeOtherTags, excludeOtherProcesses)

									// Success
									r.success()
								} catch(e: Exception) {
									logger.error("Failed to update account info:")
									e.printStackTrace()
									r.error("Database error")
								}
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch account by email:")
							e.printStackTrace()
							r.error("Database error")
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Edits an account's info and permissions
		// Permissions:
		//  - accounts.edit
		//  - accounts.password (only required if changing an account's password)
		//  - Administrator privileges (only required if changing an account's administrator status, or if the account being edited is an administrator)
		// Route parameters:
		//  - id: Integer, the ID of the account to edit
		// Parameters:
		//  - name (optional): String with max of 64 characters, the new name of the account
		//  - email (optional): String with max of 64 characters, the new email address of the account
		//  - admin (optional): Bool, whether the account will be an administrator (requires administrator privileges to change)
		//  - permissions (optional): JSON array, the new permissions for the account
		router().post("/api/v1/account/:id/edit").virtualHost(hostname).handler { r ->
			val params = r.request().params()
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.edit")) {
					try {
						val id: Int
						try {
							id = r.pathParam("id").toInt()
						} catch(e: Exception) {
							r.error("Invalid account ID")
							return@launch
						}

						// Disallow editing self
						if(id == r.userId()) {
							r.error("You cannot edit your own account")
							return@launch
						}

						// Fetch account
						val accountRes = accountsModel.fetchAccountById(id)

						// Check if it exists
						if(accountRes.count() > 0) {
							// Fetch account info
							val account = accountRes.iterator().next()

							// Check if editor has permission to edit account
							if((account.admin && r.account().hasAdminPermission()) || !account.admin) {
								// Request validation
								val v = RequestValidator()
										.optionalParam("name", Presets.accountNameValidator(), account.name)
										.optionalParam("email", EmailValidator().trim(), account.email)
										.optionalParam("permissions", PermissionsValidator(), account.permissions.toJsonArray())
										.optionalParam("admin", BooleanValidator(), account.admin)
										.optionalParam("password", PasswordValidator())

								if(v.validate(r)) {
									// Resolve edit values
									val name = v.parsedParam("name") as String
									val email = v.parsedParam("email") as String
									val perms = (v.parsedParam("permissions") as JsonArray).toStringArray()
									val admin = if(r.account().hasAdminPermission() && r.account().id != id)
										v.parsedParam("admin") as Boolean
									else
										account.admin
									val password = v.parsedParam("password") as String?

									// Make sure non-admin cannot create an admin account
									if(params.contains("admin") && admin && !r.account().hasAdminPermission()) {
										r.error("Must be an administrator to make an administrator account")
										return@launch
									}

									try {
										var emailExists = false

										if(email != account.email) {
											// Check if account with that email already exists
											val emailRes = accountsModel.fetchAccountByEmail(email)

											emailExists = if(emailRes.count() > 0)
												account.id != emailRes.iterator().next().id
											else
												false
										}

										if(emailExists) {
											r.error("Account with that email already exists")
										} else {
											try {
												// Update info
												accountsModel.updateAccountInfo(id, name, email, admin, perms)

												// Check if password specified
												if(password != null) {
													// Check for permission
													if(r.protectWithPermission("accounts.password")) {
														// Update password
														accountsModel.updateAccountHash(id, crypt.hashPassword(password)!!)
													} else {
														return@launch
													}
												}

												r.success()
											} catch(e: Exception) {
												logger.error("Failed to edit account info:")
												e.printStackTrace()
												r.error("Database error")
											}
										}
									} catch(e: Exception) {
										logger.error("Failed to fetch account by email:")
										e.printStackTrace()
										r.error("Database error")
									}
								} else {
									if(v.validationErrorType == "INVALID_EMAIL")
										r.error("Invalid email")
									else
										r.error(v)
								}
							} else {
								r.error("Only administrators may edit administrator accounts")
							}
						} else {
							r.error("Account does not exist")
						}
					} catch(e: Exception) {
						logger.error("Failed to fetch account:")
						e.printStackTrace()
						r.error("Database error")
					}
				}
			}
		}

		// Creates a new account
		// Permissions:
		//  - Administrator privileges
		// Parameters:
		//  - name: String with max of 64 characters, the name of the new account
		//  - email: String with max of 64 characters, the email address of the new account
		//  - admin: Bool, whether the new account will be an administrator
		//  - permissions: JSON array, the permissions the new account will have
		//  - password: String, the password for the new account
		router().post("/api/v1/accounts/create").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.create")) {
					// Request validation
					val v = RequestValidator()
							.param("name", Presets.accountNameValidator())
							.param("email", EmailValidator())
							.param("admin", BooleanValidator())
							.param("permissions", PermissionsValidator())
							.param("password", PasswordValidator())

					if(v.validate(r)) {
						try {
							// Collect and process parameters
							val name = v.parsedParam("name") as String
							val email = v.parsedParam("email") as String
							val perms = (v.parsedParam("permissions") as JsonArray).toStringArray()
							val admin = v.parsedParam("admin") as Boolean
							val password = v.parsedParam("password") as String

							// Make sure non-admin cannot create an admin account
							if(admin && !r.account().hasAdminPermission()) {
								r.error("Must be an administrator to make an administrator account")
								return@launch
							}

							// Check if account with same email already exists
							try {
								val accountRes = accountsModel.fetchAccountByEmail(email)

								if(accountRes.count() > 0) {
									r.error("Account with that email already exists")
								} else {
									try {
										// Create account
										createAccount(name, email, admin, perms, password)

										// Send success
										r.success()
									} catch(e: Exception) {
										logger.error("Failed to create new account:")
										e.printStackTrace()
										r.error("Database error")
									}
								}
							} catch(e: Exception) {
								logger.error("Failed to fetch account:")
								e.printStackTrace()
								r.error("Database error")
							}
						} catch(e: Exception) {
							// Invalid tags JSON array, or invalid admin value
							r.error("Invalid parameters")
							e.printStackTrace()
						}
					} else {
						r.error(v)
					}
				}
			}
		}

		// Deletes an account
		// Permissions:
		//  - Administrator privileges
		// Route parameters:
		//  - id: Integer, the ID of the account to delete
		router().post("/api/v1/account/:id/delete").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.account().hasAdminPermission()) {
					try {
						val id = r.pathParam("id").toInt()

						// Make sure this route cannot be accessed from API keys
						if(id == r.userId() && r.account().isApiKey) {
							r.unauthorized()
							return@launch
						}

						// Stop user from deleting their own account
						if(id == r.userId()) {
							r.error("Cannot delete your own account")
							return@launch
						}

						try {
							// Fetch account
							val accountRes = accountsModel.fetchAccountById(id)

							// Check if it exists
							if(accountRes.count() > 0) {
								try {
									// Delete account
									deleteAccount(id)

									// Send success
									r.success()
								} catch(e: Exception) {
									logger.error("Failed to delete account:")
									e.printStackTrace()
									r.error("Database error")
								}
							} else {
								r.error("Account does not exist")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch account:")
							e.printStackTrace()
							r.error("Database error")
						}
					} catch(e: Exception) {
						r.error("Invalid parameters")
					}
				} else {
					r.unauthorized()
				}
			}
		}
	}
}