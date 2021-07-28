package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
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
@DelicateCoroutinesApi
fun accountsController() {
	val accountsModel = AccountsModel()

	for(hostname in appHostnames()) {
		// Returns a list of accounts
		// Permissions:
		//  - accounts.list
		// Parameters:
		//  - query (optional): String, the query to use for searching account names
		//  - offset (optional): Integer at least 0 that sets the offset of returned results
		//  - limit (optional): Integer from 0 to 100, sets the amount of results to return
		//  - order (optional): Integer from 0 to 5, denotes the type of sorting to use (date newest to oldest, date oldest to newest, name alphabetically ascending, name alphabetically descending, email alphabetically ascending, email alphabetically descending)
		router().get("/api/v1/accounts").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.list")) {
					// Request validation
					val v = RequestValidator()
							.offsetLimitOrder(5)
							.optionalParam("query", StringValidator())

					if(v.validate(r)) {
						// Collect parameters
						val query = v.parsedParam("query") as String?
						val offset = v.parsedParam("offset") as Int
						val limit = v.parsedParam("limit") as Int
						val order = v.parsedParam("order") as Int

						try {
							// Fetch accounts
							val accounts = if(query == null)
								accountsModel.fetchAccountList(offset, limit, order)
							else
								accountsModel.fetchAccountsByPlaintextQuery(query, offset, limit, order)

							// Send accounts
							r.success(json {obj(
									"accounts" to accounts.toJsonArray()
							)})
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
		//  - currentPassword (optional): String, the user's current password (required to change email or password)
		//  - name (optional): String with max of 64 characters, the new name of the account
		//  - email (optional): String with max of 64 characters, the new email address of the account (requires currentPassword to be provided)
		//  - password (optional): String, the new password for this account (requires currentPassword to be provided)
		//  - defaultSource (optional): Int, the ID of the user's default media source
		//  - excludeTags (optional): JSON array, tags to globally exclude when listing files (from searches, lists, or anywhere else an array of files would be returned other than file children and lists)
		//  - excludeOtherMedia (optional): Bool, whether to globally exclude media created by other users when viewing or listing any media
		//  - excludeOtherLists (optional): Bool, whether to globally exclude lists created by other users
		//  - excludeOtherTags (optional): Bool, whether to globally exclude tags added to files created by other users
		//  - excludeOtherProcesses (optional): Bool, whether to globally exclude processes created by other users
		//  - excludeOtherSources (optional: Bool, whether to globally exclude sources created by other users
		router().post("/api/v1/account/self/edit").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectNonApiKey()) {
					val acc = r.account()
					val sourcesModel = SourcesModel(acc)

					// Request validation
					val v = RequestValidator()
							.optionalParam("currentPassword", StringValidator()
									.maxLength(64)
									.noNewlinesOrControlChars())
							.optionalParam("name", Presets.accountNameValidator(), acc.name)
							.optionalParam("email", EmailValidator().trim())
							.optionalParam("password", PasswordValidator())
							.optionalParam("defaultSource", IntValidator(), acc.defaultSource)
							.optionalParam("excludeTags", TagsValidator(), acc.excludeTags.toJsonArray())
							.optionalParam("excludeOtherMedia", BooleanValidator(), acc.excludeOtherMedia)
							.optionalParam("excludeOtherLists", BooleanValidator(), acc.excludeOtherLists)
							.optionalParam("excludeOtherTags", BooleanValidator(), acc.excludeOtherTags)
							.optionalParam("excludeOtherProcesses", BooleanValidator(), acc.excludeOtherProcesses)
							.optionalParam("excludeOtherSources", BooleanValidator(), acc.excludeOtherSources)

					if(v.validate(r)) {
						// Get edit values
						val currentPassword = v.parsedParam("currentPassword") as String?
						val name = v.parsedParam("name") as String
						val email = v.parsedParam("email") as String?
						val password = v.parsedParam("password") as String?
						val defaultSource = v.parsedParam("defaultSource") as Int
						val excludeTags = (v.parsedParam("excludeTags") as JsonArray).toStringArray()
						val excludeOtherMedia = v.parsedParam("excludeOtherMedia") as Boolean
						val excludeOtherLists = v.parsedParam("excludeOtherLists") as Boolean
						val excludeOtherTags = v.parsedParam("excludeOtherTags") as Boolean
						val excludeOtherProcesses = v.parsedParam("excludeOtherProcesses") as Boolean
						val excludeOtherSources = v.parsedParam("excludeOtherSources") as Boolean

						try {
							// Check trying to change password or email and current password is not provided
							if(currentPassword == null && (email != null || password != null)) {
								r.error("Must provide current password to change email or password")
								return@launch
							} else if(currentPassword != null && !crypt.verifyPassword(currentPassword, acc.hash)) { // Validate password
								r.error("Password does not match existing password")
								return@launch
							}

							// Check if account with provided email already exists
							if(email != null && email != r.account().email) {
								val emailRes = accountsModel.fetchAccountByEmail(email)

								if(emailRes.count() > 0) {
									r.error("Account with that email already exists")
									return@launch
								}
							}

							// Check if new media source exists and is accessible to the user
							if(defaultSource != acc.defaultSource) {
								val srcRes = sourcesModel.fetchSource(defaultSource)

								if(srcRes.rowCount() < 1) {
									r.error("Invalid media source")
									return@launch
								}
							}

							try {
								// Hash password if provided
								val hash = if(password == null) null else crypt.hashPassword(password)

								// Update info
								accountsModel.updateAccountInfo(acc.id, name, email, hash, defaultSource, excludeTags, excludeOtherMedia, excludeOtherLists, excludeOtherTags, excludeOtherProcesses, excludeOtherSources)

								// Success
								r.success()
							} catch(e: Exception) {
								logger.error("Failed to update account info:")
								e.printStackTrace()
								r.error("Database error")
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
		//  - password (optional): String, the account's new password
		router().post("/api/v1/account/:id/edit").virtualHost(hostname).handler { r ->
			val params = r.request().params()
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.edit")) {
					try {
						val id = try {
							r.pathParam("id").toInt()
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
							val account = accountRes.first()

							// Check if editor has permission to edit account
							if((account.admin && r.account().hasAdminPermission()) || !account.admin) {
								// Request validation
								val v = RequestValidator()
										.optionalParam("name", Presets.accountNameValidator(), account.name)
										.optionalParam("email", EmailValidator().trim(), account.email)
										.optionalParam("permissions", PermissionsValidator(), account.permissions.toJsonArray())
										.optionalParam("admin", BooleanValidator(), account.admin)
										.optionalParam("password", PasswordValidator())
										.optionalParam("defaultSource", IntValidator())

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
									val defaultSource = v.parsedParam("defaultSource") as Int?

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
											// Check if source exists (if specified)
											if(defaultSource != null) {
												val sourcesModel = SourcesModel(r.account())
												val sourceRes = try {
													sourcesModel.fetchSource(defaultSource)
												} catch(e: Exception) {
													logger.error("Failed to fetch source with ID $defaultSource:")
													e.printStackTrace()
													r.error("Internal error")
													return@launch
												}

												if(sourceRes.rowCount() < 1) {
													r.error("Source does not exist or is not accessible to you")
													return@launch
												}
											}

											try {
												// Update info
												accountsModel.updateAccountInfo(id, name, email, admin, perms, defaultSource ?: account.defaultSource)

												// Check if password specified
												if(password != null) {
													// Check for permission
													if(r.protectWithPermission("accounts.password")) {
														// Update password
														accountsModel.updateAccountHash(id, crypt.hashPassword(password))
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
		//  - defaultSource: Int, the media source ID to use as this account's default
		router().post("/api/v1/accounts/create").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectWithPermission("accounts.create")) {
					val sourcesModel = SourcesModel(r.account())

					// Request validation
					val v = RequestValidator()
							.param("name", Presets.accountNameValidator())
							.param("email", EmailValidator())
							.param("admin", BooleanValidator())
							.param("permissions", PermissionsValidator())
							.param("password", PasswordValidator())
							.param("defaultSource", IntValidator())

					if(v.validate(r)) {
						try {
							// Collect and process parameters
							val name = v.parsedParam("name") as String
							val email = v.parsedParam("email") as String
							val perms = (v.parsedParam("permissions") as JsonArray).toStringArray()
							val admin = v.parsedParam("admin") as Boolean
							val password = v.parsedParam("password") as String
							val defaultSource = v.parsedParam("defaultSource") as Int

							// Make sure non-admin cannot create an admin account
							if(admin && !r.account().hasAdminPermission()) {
								r.error("Must be an administrator to make an administrator account")
								return@launch
							}

							// Check if account with same email already exists
							val accountRes = try {
								accountsModel.fetchAccountByEmail(email)
							} catch(e: Exception) {
								logger.error("Failed to fetch account by email \"$email\":")
								e.printStackTrace()
								r.error("Database error")
								return@launch
							}
							if(accountRes.count() > 0) {
								r.error("Account with that email already exists")
								return@launch
							}

							// Check if source exists and is visible to the creator
							val sourceRes = try {
								sourcesModel.fetchSource(defaultSource)
							} catch(e: Exception) {
								logger.error("Failed to fetch source by ID $defaultSource:")
								e.printStackTrace()
								r.error("Database error")
								return@launch
							}
							if(sourceRes.rowCount() < 1) {
								r.error("Source does not exist or is not visible to you")
								return@launch
							}

							try {
								// Create account
								val id = createAccount(name, email, admin, perms, password, defaultSource)

								// Send newly created account's ID
								r.success(json {obj(
										"id" to id
								)})
							} catch(e: Exception) {
								logger.error("Failed to create new account:")
								e.printStackTrace()
								r.error("Database error")
							}
						} catch(e: Exception) {
							logger.error("Failed to fetch account:")
							e.printStackTrace()
							r.error("Database error")
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