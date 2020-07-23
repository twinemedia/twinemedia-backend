package net.termer.twinemedia.util

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Module.Companion.crypt
import net.termer.twinemedia.model.AccountsModel
import java.text.SimpleDateFormat
import java.util.regex.Pattern

private val emailPattern : Pattern = Pattern.compile("^\\w+@[a-zA-Z_0-9\\-]+?\\.[a-zA-Z]{2,3}$")
private val accountsModel = AccountsModel()
private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

/**
 * Converts the JSON data from an account row to a UserAccount object
 * @param accountJson The JSON data from an account row to convert
 * @since 1.2.0
 */
@Suppress("UNCHECKED_CAST")
fun accountJsonToObject(accountJson: JsonObject): UserAccount {
    // Add permissions
    val permsArray = JsonArray(accountJson.getString("account_permissions"))
    val permissions = arrayOfNulls<String>(permsArray.size())

    for((index, item) in permsArray.list.withIndex())
        permissions[index] = item.toString()

    // Add permissions
    val excludeArray = JsonArray(accountJson.getString("account_exclude_tags"))
    val excludeTags = arrayOfNulls<String>(excludeArray.size())

    for((index, item) in excludeArray.list.withIndex())
        excludeTags[index] = item.toString()

    return UserAccount(
            id = accountJson.getInteger("id"),
            email = accountJson.getString("account_email"),
            name = accountJson.getString("account_name"),
            permissions = permissions as Array<String>,
            admin = accountJson.getBoolean("account_admin"),
            hash = accountJson.getString("account_hash"),
            creationDate = simpleDateFormat.parse(accountJson.getString("account_creation_date")),
            excludeTags = excludeTags as Array<String>,
            excludeOtherMedia = accountJson.getBoolean("account_exclude_other_media"),
            excludeOtherLists = accountJson.getBoolean("account_exclude_other_lists"),
            excludeOtherTags = accountJson.getBoolean("account_exclude_other_tags"),
            excludeOtherProcesses = accountJson.getBoolean("account_exclude_other_processes")
    )
}

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

    accountsModel.createAccountEntry(email, name, admin, permissions, hash.orEmpty())
}

/**
 * Updates an account's password
 * @param id The ID of the account
 * @param newPassword The new password for the account
 * @since 1.0
 */
suspend fun updateAccountPassword(id : Int, newPassword : String) {
    val hash = crypt.hashPassword(newPassword).orEmpty()

    accountsModel.updateAccountHash(id, hash)
}

/**
 * Returns whether the provided email is valid
 * @return Whether the provided email is valid
 * @since 1.0
 */
fun validEmail(email : String) = emailPattern.matcher(email).matches()