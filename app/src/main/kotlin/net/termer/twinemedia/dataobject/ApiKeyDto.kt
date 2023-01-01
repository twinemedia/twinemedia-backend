package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * DTO for an API key
 * @since 2.0.0
 */
class ApiKeyDto(
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
	 * The key's owner
	 * @since 2.0.0
	 */
	val owner: RecordOwnerDto,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): JsonSerializable(), StandardRow {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"permissions" to permissions,
		"jwt" to jwt,
		"owner" to owner.toJson(),
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = ApiKeyDto(
			internalId = row.getInteger("id"),
			id = row.getString("key_id"),
			name = row.getString("key_name"),
			permissions = row.getArrayOfStrings("key_permissions"),
			jwt = row.getString("key_jwt"),
			owner = RecordOwnerDto(
				id = row.getString("key_owner_id"),
				name = row.getString("key_owner_name")
			),
			createdTs = row.getOffsetDateTime("key_created_ts"),
			modifiedTs = row.getOffsetDateTime("key_modified_ts")
		)
	}
}
