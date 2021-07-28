package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.containsPermission
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an account's info
 * @param id The account's ID
 * @param email The account's email address
 * @param name The account's name
 * @param admin Whether the account is an admin
 * @param permissions An array of the account's permissions
 * @param defaultSource The ID of the account's default media source
 * @param defaultSourceName The name of the account's default media source (or null if the source no longer exists)
 * @param defaultSourceType The type of the account's default media source (or null if the source no longer exists)
 * @param creationDate The date this account was created on
 * @since 1.4.0
 */
@DataObject
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
		 * The ID of the account's default media source
		 * @since 1.5.0
		 */
		val defaultSource: Int,

		/**
		 * The name of the account's default media source (or null if the source no longer exists)
		 * @since 1.5.0
		 */
		val defaultSourceName: String?,

		/**
		 * The type of the account's default media source (or null if the source no longer exists)
		 */
		val defaultSourceType: String?,

		/**
		 * The date this account was created on
		 * @since 1.4.0
		 */
		val creationDate: OffsetDateTime,

		/**
		 * The amount of files this account has created
		 * @since 1.4.0
		 */
		val filesCreated: Int
): SerializableDataObject {
	/**
	 * Returns if this user account has the specified permission
	 * @param permission The permission to check
	 * @return Whether this user account has the specified permission
	 * @since 1.4.0
	 */
	fun hasPermission(permission: String): Boolean {
		return admin || permissions.containsPermission(permission)
	}

	/**
	 * Returns whether this user has administrator permissions
	 * @return whether this user has administrator permissions
	 * @since 1.4.0
	 */
	fun hasAdminPermission() = admin

	override fun toJson() = json {obj(
			"id" to id,
			"email" to email,
			"name" to name,
			"permissions" to permissions,
			"admin" to admin,
			"default_source" to defaultSource,
			"default_source_name" to defaultSourceName,
			"default_source_type" to defaultSourceType,
			"creation_date" to creationDate.toString(),
			"files_created" to filesCreated
	)}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<AccountInfo> { row ->
			AccountInfo(
					id = row.getInteger("id"),
					email = row.getString("email"),
					name = row.getString("name"),
					admin = row.getBoolean("admin"),
					permissions = row.getJsonArray("permissions").toStringArray(),
					defaultSource = row.getInteger("default_source"),
					defaultSourceName = row.getString("default_source_name"),
					defaultSourceType = row.getString("default_source_type"),
					creationDate = row.getOffsetDateTime("creation_date"),
					filesCreated = row.getInteger("files_created")
			)
		}
	}
}