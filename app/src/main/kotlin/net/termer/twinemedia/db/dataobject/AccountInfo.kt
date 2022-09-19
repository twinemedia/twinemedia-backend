package net.termer.twinemedia.db.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.containsPermission
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an account's info
 * @since 1.4.0
 */
class AccountInfo(
	/**
	 * The account's ID
	 * @since 1.4.0
	 */
	val id: Int,

	/**
	 * The account's email address
	 * @since 1.4.0
	 */
	val email: String,

	/**
	 * The account's name
	 * @since 1.4.0
	 */
	val name: String,

	/**
	 * Whether the account is an admin
	 * @since 1.4.0
	 */
	val admin: Boolean,

	/**
	 * An array of the account's permissions
	 * @since 1.4.0
	 */
	val permissions: Array<String>,

	/**
	 * The ID of the account's default file source
	 * @since 1.5.0
	 */
	val defaultSource: Int,

	/**
	 * The name of the account's default file source (or null if the source no longer exists)
	 * @since 1.5.0
	 */
	val defaultSourceName: String?,

	/**
	 * The type of the account's default file source (or null if the source no longer exists)
	 */
	val defaultSourceType: String?,

	/**
	 * The amount of files this account has created
	 * @since 1.4.0
	 */
	val filesCreated: Int,

	/**
	 * The account's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The account's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
): JsonSerializable {
	/**
	 * Returns if this user account has the specified permission
	 * @param permission The permission to check
	 * @return Whether this user account has the specified permission
	 * @since 1.4.0
	 */
	fun hasPermission(permission: String) = admin || permissions.containsPermission(permission)

	/**
	 * Returns whether this user has administrator permissions
	 * @return whether this user has administrator permissions
	 * @since 1.4.0
	 */
	fun hasAdminPermission() = admin

	override fun toJson() = jsonObjectOf(
		"id" to id,
		"email" to email,
		"name" to name,
		"permissions" to permissions,
		"admin" to admin,
		"default_source" to defaultSource,
		"default_source_name" to defaultSourceName,
		"default_source_type" to defaultSourceType,
		"files_created" to filesCreated,
		"created_ts" to createdTs.toString(),
		"modified_ts" to modifiedTs.toString()
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			AccountInfo(
				id = row.getInteger("id"),
				email = row.getString("email"),
				name = row.getString("name"),
				admin = row.getBoolean("admin"),
				permissions = row.getJsonArray("permissions").toStringArray(),
				defaultSource = row.getInteger("default_source"),
				defaultSourceName = row.getString("default_source_name"),
				defaultSourceType = row.getString("default_source_type"),
				filesCreated = row.getInteger("files_created"),
				createdTs = row.getOffsetDateTime("created_ts"),
				modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}