package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.containsColumn
import net.termer.twinemedia.util.containsPermission
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an account
 * @param id The account's ID
 * @param email The account's email address
 * @param name The account's name
 * @param permissions An array of the account's permissions
 * @param admin Whether the account is an admin
 * @param hash The account's password hash
 * @param creationDate The date this account was created on
 * @param isApiKey Whether this account is being accessed by an API key
 * @param keyPermissions An array of permissions that this key is authorized to use
 * @since 1.2.0
 */
@DataObject
class Account(
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
        val creationDate: OffsetDateTime,

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
        val excludeOtherProcesses: Boolean,

        /**
         * Whether this account is being accessed by an API key
         * @since 1.3.0
         */
        val isApiKey: Boolean = false,

        /**
         * An array of permissions that this key is authorized to use
         * @since 1.3.0
         */
        val keyPermissions: Array<String>? = null
) {
	/**
	 * Returns if this user account has the specified permission
	 * @param permission The permission to check
	 * @return Whether this user account has the specified permission
	 * @since 1.0
	 */
	fun hasPermission(permission: String): Boolean {
		return if(isApiKey && keyPermissions != null)
			(admin || permissions.containsPermission(permission)) && keyPermissions.containsPermission(permission)
		else
			admin || permissions.containsPermission(permission)
	}

	/**
	 * Returns whether this user has administrator permissions
	 * @return whether this user has administrator permissions
	 * @since 1.3.0
	 */
	fun hasAdminPermission() = !isApiKey && admin

	companion object {
        /**
         * The row mapper for this type of row
         * @since 1.4.0
         */
		val MAPPER = RowMapper<Account> { row ->
			Account(
                    id = row.getInteger("id"),
                    email = row.getString("account_email"),
                    name = row.getString("account_name"),
                    permissions = row.getJsonArray("account_permissions").toStringArray(),
                    admin = row.getBoolean("account_admin"),
                    hash = row.getString("account_hash"),
                    creationDate = row.getOffsetDateTime("account_creation_date"),
                    excludeTags = row.getJsonArray("account_exclude_tags").toStringArray(),
                    excludeOtherMedia = row.getBoolean("account_exclude_other_media"),
                    excludeOtherLists = row.getBoolean("account_exclude_other_lists"),
                    excludeOtherTags = row.getBoolean("account_exclude_other_tags"),
                    excludeOtherProcesses = row.getBoolean("account_exclude_other_processes"),
                    isApiKey = row.containsColumn("key_id"),
                    keyPermissions = if(row.containsColumn("key_id"))
	                    row.getJsonArray("key_permissions").toStringArray()
                    else null
            )
		}
	}
}