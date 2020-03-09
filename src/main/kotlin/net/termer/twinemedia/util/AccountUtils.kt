package net.termer.twinemedia.util

import io.vertx.core.json.JsonArray
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.model.createAccountEntry

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