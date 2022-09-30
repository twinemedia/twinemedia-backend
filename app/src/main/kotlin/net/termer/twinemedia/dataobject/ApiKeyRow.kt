package net.termer.twinemedia.dataobject

import io.vertx.sqlclient.templates.RowMapper
import java.time.OffsetDateTime

/**
 * Data class for an API key
 * @since 1.4.0
 */
class ApiKeyRow(
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
	 * The key creator's account ID
	 * @since 2.0.0
	 */
	val creatorId: Int,

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
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			ApiKeyRow(
				internalId = row.getInteger("id"),
				id = row.getString("key_id"),
				name = row.getString("key_name"),
				permissions = row.getArrayOfStrings("key_permissions"),
				jwt = row.getString("key_jwt"),
				creatorId = row.getInteger("key_creator"),
				createdTs = row.getOffsetDateTime("key_created_ts"),
				modifiedTs = row.getOffsetDateTime("key_modified_ts")
			)
		}
	}
}