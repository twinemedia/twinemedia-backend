package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toJsonArray
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an API key's info
 * @param id The key's alphanumeric ID
 * @param name The user-defined name of the key
 * @param permissions The permissions this key was granted
 * @param jwt The key's JWT authentication token
 * @param owner The owner of this key, the subject of the JWT token
 * @param ownerName The name of the key's owner
 * @param createdOn The time this key was created
 * @since 1.4.0
 */
@DataObject
class ApiKeyInfo(
		/**
		 * The key's alphanumeric ID
		 * @since 1.4.0
		 */
		val id: String,
		/**
		 * The user-defined name of the key
		 * @since 1.4.0
		 */
		val name: String,
		/**
		 * The permissions this key was granted
		 * @since 1.4.0
		 */
		val permissions: Array<String>,
		/**
		 * The key's JWT authentication token
		 * @since 1.4.0
		 */
		val jwt: String,
		/**
		 * The owner of this key, the subject of the JWT token
		 * @since 1.4.0
		 */
		val owner: Int,
		/**
		 * The name of the key's owner (null if the account doesn't exist)
		 * @since 1.4.0
		 */
		val ownerName: String?,
		/**
		 * The time this key was created
		 * @since 1.4.0
		 */
		val createdOn: OffsetDateTime
) {
	/**
	 * Returns a JSON representation of the key's info
	 * @return A JSON representation of the key's info
	 * @since 1.4.0
	 */
	fun toJson() = json {
		obj(
				"id" to id,
				"name" to name,
				"permissions" to permissions.toJsonArray(),
				"jwt" to jwt,
				"owner" to owner,
				"owner_name" to ownerName,
				"created_on" to createdOn.toString()
		)
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<ApiKeyInfo> { row ->
			ApiKeyInfo(
					id = row.getString("id"),
					name = row.getString("name"),
					permissions = row.getJsonArray("permissions").toStringArray(),
					jwt = row.getString("jwt"),
					owner = row.getInteger("owner"),
					ownerName = row.getString("owner_name"),
					createdOn = row.getOffsetDateTime("created_on")
			)
		}
	}
}