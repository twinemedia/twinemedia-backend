package net.termer.twinemedia.db.dataobject

import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an API key
 * @since 1.4.0
 */
class ApiKey(
	/**
	 * The key's internal sequential ID
	 * @since 1.4.0
	 */
	val internalId: Int,

	/**
	 * The key's alphanumeric ID
	 * @since 2.0.0
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
	 * The key's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The key's last modified timestamp
	 */
	val modifiedTs: OffsetDateTime
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			ApiKey(
				internalId = row.getInteger("id"),
				id = row.getString("key_id"),
				name = row.getString("key_name"),
				permissions = row.getJsonArray("key_permissions").toStringArray(),
				jwt = row.getString("key_jwt"),
				owner = row.getInteger("key_owner"),
				createdTs = row.getOffsetDateTime("key_created_ts"),
				modifiedTs = row.getOffsetDateTime("key_modified_ts")
			)
		}
	}
}