package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.toJsonArray
import java.time.OffsetDateTime

/**
 * DTO for an API key
 * @since 1.4.0
 */
class ApiKeyDto(
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
	 * The key's creator
	 * @since 1.4.0
	 */
	val creator: RecordCreatorDto,

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
): JsonSerializable() {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"permissions" to permissions,
		"jwt" to jwt,
		"creator" to creator.toJson(),
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = ApiKeyDto(
			id = row.getString("key_id"),
			name = row.getString("key_name"),
			permissions = row.getArrayOfStrings("key_permissions"),
			jwt = row.getString("key_jwt"),
			creator = RecordCreatorDto(
				id = row.getString("key_creator_id"),
				name = row.getString("key_creator_name")
			),
			createdTs = row.getOffsetDateTime("key_created_ts"),
			modifiedTs = row.getOffsetDateTime("key_modified_ts")
		)
	}
}