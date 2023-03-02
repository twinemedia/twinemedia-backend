package net.termer.twinemedia.util.account

import io.vertx.core.Vertx
import net.termer.twinemedia.dataobject.SelfAccountDto
import net.termer.twinemedia.util.containsPermission

/**
 * Account context for an authenticated user
 * @since 2.0.0
 */
class AccountContext(
    /**
     * The associated Vert.x instance
     * @since 2.0.0
     */
    val vertx: Vertx,

    /**
     * The user's self-account info
     * @since 2.0.0
     */
    val selfAccount: SelfAccountDto
) {
    /**
     * Returns whether the account has the specified permission
     * @param permission The permission to check
     * @return Whether the account has the specified permission
     * @since 2.0.0
     */
    fun hasPermission(permission: String): Boolean {
        if (selfAccount.isApiKey) {
            // If this is an API key but the permissions list is null, then it is not safe to continue
            if (selfAccount.keyPermissions == null)
                return false

            // Check if both the account AND the API key have permission
            val accHasPerm = selfAccount.isAdmin || selfAccount.permissions.containsPermission(permission)
            val keyHasPerm = selfAccount.keyPermissions.containsPermission(permission)
            return accHasPerm && keyHasPerm
        } else {
            // If the account is an admin, we know he/she has permission, otherwise we check the permissions list
            return selfAccount.isAdmin || selfAccount.permissions.containsPermission(permission)
        }
    }

    /**
     * Returns whether the account has all the specified permissions
     * @param permissions The permissions to check
     * @return Whether the account has all the specified permissions
     * @since 2.0.0
     */
    fun hasPermissions(permissions: Iterable<String>): Boolean {
        // It's more efficient to have a separate branch for checks in the case of an API key, even if it's repetitive
        if (selfAccount.isApiKey) {
            // If this is an API key but the permissions list is null, then it is not safe to continue
            if (selfAccount.keyPermissions == null)
                return false

            // Check if any permissions are missing from either the account or the API key
            val keyPerms = selfAccount.keyPermissions
            if (selfAccount.isAdmin) {
                // The account is an admin, so we don't need to check for the permission in the account each iteration
                for (perm in permissions)
                    if (!keyPerms.containsPermission(perm))
                        return false
            } else {
                val accPerms = selfAccount.permissions
                for (perm in permissions)
                    if (!accPerms.containsPermission(perm) || !keyPerms.containsPermission(perm))
                        return false
            }

            // None were missing, therefore the account and the key have the permission
            return true
        } else {
            // Admin accounts that aren't being accessed through an API key implicitly have all permissions
            if (selfAccount.isAdmin)
                return true

            // Check if any permissions are missing
            val accPerms = selfAccount.permissions
            for (perm in permissions)
                if (!accPerms.containsPermission(perm))
                    return false

            // None were missing, therefore the account has the permission
            return true
        }
    }

    /**
     * Returns whether the account has administrator permissions.
     * If [selfAccount] is an API key, then this method will return false.
     * @return Whether the account has administrator permissions
     * @since 2.0.0
     */
    fun hasAdminPermission() = selfAccount.isAdmin && !selfAccount.isApiKey
}
