package net.termer.twinemedia.dataobject

import io.vertx.sqlclient.Row
import java.time.OffsetDateTime

/**
 * Data class for an API key
 * @since 2.0.0
 */
class ApiKeyRow(
	override val internalId: Int,
	override val id: String,

	/**
	 * The user-defined name of the key
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The permissions this key was granted
	 * @since 2.0.0
	 */
	val permissions: Array<String>,

	/**
	 * The key's JWT authentication token
	 * @since 2.0.0
	 */
	val jwt: String,

	/**
	 * The key creator's account internal ID
	 * @since 2.0.0
	 */
	val creatorInternalId: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): StandardRow {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = ApiKeyRow(
			internalId = row.getInteger("id"),
			id = row.getString("key_id"),
			name = row.getString("key_name"),
			permissions = row.getArrayOfStrings("key_permissions"),
			jwt = row.getString("key_jwt"),
			creatorInternalId = row.getInteger("key_creator"),
			createdTs = row.getOffsetDateTime("key_created_ts"),
			modifiedTs = row.getOffsetDateTime("key_modified_ts")
		)
	}
}