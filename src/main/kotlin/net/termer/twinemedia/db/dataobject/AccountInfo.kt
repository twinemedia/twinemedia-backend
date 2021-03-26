package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
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
 * @param permissions An array of the account's permissions
 * @param admin Whether the account is an admin
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
		 * The date this account was created on
		 * @since 1.4.0
		 */
		val creationDate: OffsetDateTime,

		/**
		 * The amount of files this account has created
		 * @since 1.4.0
		 */
		val filesCreated: Int
) {
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

	/**
	 * Returns a JSON representation of the account's info
	 * @return A JSON representation of the account's info
	 * @since 1.4.0
	 */
	fun toJson() = json {
		obj(
				"id" to id,
				"email" to email,
				"name" to name,
				"permissions" to permissions,
				"admin" to admin,
				"creation_date" to creationDate.toString(),
				"files_created" to filesCreated
		)
	}

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
					permissions = row.getJsonArray("permissions").toStringArray(),
					admin = row.getBoolean("admin"),
					creationDate = row.getOffsetDateTime("creation_date"),
					filesCreated = row.getInteger("files_created")
			)
		}
	}
}