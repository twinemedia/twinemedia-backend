package net.termer.twinemedia.util

import java.util.*

/**
 * Utility class to expose info about the account associated with a request
 * @param id The account's ID
 * @param email The account's email address
 * @param name The account's name
 * @param permissions An array of the account's permissions
 * @param admin Whether the account is an admin
 * @param hash The account's password hash
 * @param creationDate The date this account was created on
 * @since 1.2.0
 */
class UserAccount(
    /**
     * The account's ID
     * @since 1.2.0
     */
    val id: Int,

    /**
     * The account's email address
     * @since 1.2.0
     */
    val email: String,

    /**
     * The account's name
     * @since 1.2.0
     */
    val name: String,

    /**
     * An array of the account's permissions
     * @since 1.2.0
     */
    val permissions: Array<String>,

    /**
     * Whether the account is an admin
     * @since 1.2.0
     */
    val admin: Boolean,

    /**
     * The account's password hash
     * @since 1.2.0
     */
    val hash: String,

    /**
     * The date this account was created on
     * @since 1.2.0
     */
    val creationDate: Date,

    /**
     * The tags to exclude globally when listing and searching files
     * @since 1.2.0
     */
    val excludeTags: Array<String>,

    /**
     * Whether to globally exclude media files created by other accounts
     * @since 1.2.0
     */
    val excludeOtherMedia: Boolean,

    /**
     * Whether to globally exclude lists created by other accounts
     * @since 1.2.0
     */
    val excludeOtherLists: Boolean,

    /**
     * Whether to globally exclude tags on files created by other accounts
     * @since 1.2.0
     */
    val excludeOtherTags: Boolean,

    /**
     * Whether to globally exclude process presets created by other accounts
     * @since 1.2.0
     */
    val excludeOtherProcesses: Boolean
) { 
    /**
     * Returns if the user for this request has the specified permission. Will fail if request is not authenticated.
     * @param permission The permission to check
     * @since 1.0
     */
    fun hasPermission(permission: String): Boolean {
        var has = false

        // Check if the user has the permission
        if(admin || permissions.contains(permission) || permissions.contains("$permission.all") || permissions.contains("*")) {
            has = true
        } else if(permission.contains('.')) {
            // Check permission tree
            val perm = StringBuilder()
            for(child in permission.split('.')) {
                perm.append("$child.")
                for (p in permissions)
                    if (p == "$perm*") {
                        has = true
                        break
                    }
                if (has)
                    break
            }
        }

        return has
    }
}