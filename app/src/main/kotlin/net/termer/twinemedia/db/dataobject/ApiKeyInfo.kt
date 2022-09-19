package net.termer.twinemedia.db.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.toJsonArray
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an API key's info
 * @since 1.4.0
 */
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
	 * The key's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The key's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
): JsonSerializable {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"permissions" to permissions.toJsonArray(),
		"jwt" to jwt,
		"owner" to owner,
		"owner_name" to ownerName,
		"created_ts" to createdTs.toString(),
		"modified_ts" to modifiedTs.toString()
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			ApiKeyInfo(
				id = row.getString("id"),
				name = row.getString("name"),
				permissions = row.getJsonArray("permissions").toStringArray(),
				jwt = row.getString("jwt"),
				owner = row.getInteger("owner"),
				ownerName = row.getString("owner_name"),
				createdTs = row.getOffsetDateTime("created_ts"),
				modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}