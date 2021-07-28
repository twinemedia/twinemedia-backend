package net.termer.twinemedia.util

import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.model.ApiKeysModel
import java.text.SimpleDateFormat
import java.util.regex.Pattern

private val emailPattern: Pattern = Pattern.compile("^[\\w.]+@[a-zA-Z_0-9\\-.]+?\\.[a-zA-Z]{2,16}$")
private val accountsModel = AccountsModel()
private val keysModel = ApiKeysModel()
private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

/**
 * Creates a new account with the provided details
 * @param name The name of the new account
 * @param email The email address of the new account
 * @param admin Whether the new account will be an administrator
 * @param permissions An array of permissions that the new account will have
 * @param password The password of the new account
 * @param defaultSource The default source of the new account
 * @since 1.5.0
 */
@DelicateCoroutinesApi
suspend fun createAccount(name: String, email: String, admin: Boolean, permissions: Array<String>, password: String, defaultSource: Int): Int {
	val hash = crypt.hashPassword(password)

	return accountsModel.createAccountEntry(email, name, admin, permissions, hash, defaultSource)
}

/**
 * Updates an account's password
 * @param id The ID of the account
 * @param newPassword The new password for the account
 * @since 1.0
 */
@DelicateCoroutinesApi
suspend fun updateAccountPassword(id: Int, newPassword: String) {
	val hash = crypt.hashPassword(newPassword).orEmpty()

	accountsModel.updateAccountHash(id, hash)
}

/**
 * Deletes an account and all of its API keys
 * @param id The ID of the account to delete
 * @since 1.3.0
 */
suspend fun deleteAccount(id: Int) {
	accountsModel.deleteAccount(id)
	keysModel.deleteApiKeysByAccount(id)
}

/**
 * Returns whether the provided email is valid
 * @return Whether the provided email is valid
 * @since 1.0.0
 */
fun validEmail(email: String) = emailPattern.matcher(email).matches()