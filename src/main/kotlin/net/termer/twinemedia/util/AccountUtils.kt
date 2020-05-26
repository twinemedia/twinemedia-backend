package net.termer.twinemedia.util

import io.vertx.core.json.JsonArray
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.model.createAccountEntry
import net.termer.twinemedia.model.updateAccountHash
import java.util.regex.Pattern

val emailPattern : Pattern = Pattern.compile("^\\w+@[a-zA-Z_]+?\\.[a-zA-Z]{2,3}$")

/**
 * Creates a new account with the provided details
 * @param name The name of the new account
 * @param email The email address of the new account
 * @param admin Whether the new account will be an administrator
 * @param permissions An array of permissions that the new account will have
 * @param password The password of the new account
 * @since 1.0
 */
suspend fun createAccount(name : String, email : String, admin : Boolean, permissions : JsonArray, password : String) {
    val hash = crypt.hashPassword(password)

    createAccountEntry(email, name, admin, permissions, hash.orEmpty())
}

/**
 * Updates an account's password
 * @param id The ID of the account
 * @param newPassword The new password for the account
 * @since 1.0
 */
suspend fun updateAccountPassword(id : Int, newPassword : String) {
    val hash = crypt.hashPassword(newPassword).orEmpty()

    updateAccountHash(id, hash)
}

/**
 * Returns whether the provided email is valid
 * @return Whether the provided email is valid
 * @since 1.0
 */
fun validEmail(email : String) = emailPattern.matcher(email).matches()