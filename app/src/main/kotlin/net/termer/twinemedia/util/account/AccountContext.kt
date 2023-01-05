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
     * The user's self-account info
     * @since 2.0.0
     */
    val selfAccount: SelfAccountDto,

    /**
     * The associated Vert.x instance
     * @since 2.0.0
     */
    val vertx: Vertx
) {
    /**
     * Returns if the account has the specified permission.
     *
     * @param permission The permission to check
     * @return Whether this user account has the specified permission
     * @since 2.0.0
     */
    fun hasPermission(permission: String): Boolean {
        return if(selfAccount.isApiKey && selfAccount.keyPermissions != null)
            (selfAccount.isAdmin || selfAccount.permissions.containsPermission(permission)) && selfAccount.keyPermissions.containsPermission(permission)
        else
            selfAccount.isApiKey || selfAccount.permissions.containsPermission(permission)
    }

    /**
     * Returns whether the account has administrator permissions.
     * If [selfAccount] is an API key, then this method will return false.
     * @return whether the account has administrator permissions
     * @since 2.0.0
     */
    fun hasAdminPermission() = !selfAccount.isApiKey && selfAccount.isAdmin
}
