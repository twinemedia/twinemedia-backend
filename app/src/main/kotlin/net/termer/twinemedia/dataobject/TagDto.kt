package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.db.hasCol
import java.time.OffsetDateTime

/**
 * DTO for a tag
 * @since 2.0.0
 */
class TagDto(
	override val internalId: Int,
	override val id: String,

	/**
	 * The tag's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The tag's description, or null if it was not included
	 * @since 2.0.0
	 */
	val description: String?,

	/**
	 * The tag owner's account, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val owner: RecordOwnerDto?,

	/**
	 * The number of files using the tag
	 * @since 2.0.0
	 */
	val fileCount: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): JsonSerializable(), StandardRow {
	override fun toJson(): JsonObject = jsonObjectOf(
		"id" to id,
		"name" to name,
		"description" to description,
		"owner" to owner?.toJson(),
		"fileCount" to fileCount,
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row): TagDto {
			val tagOwnerId = row.getString("tag_owner_id")

			return TagDto(
				internalId = row.getInteger("id"),
				id = row.getString("tag_id"),
				name = row.getString("tag_name"),
				description = if(row.hasCol("tag_description")) row.getString("tag_description") else null,
				owner = if(tagOwnerId == null) null else RecordOwnerDto(
					id = tagOwnerId,
					name = row.getString("tag_owner_name")
				),
				fileCount = row.getInteger("tag_file_count"),
				createdTs = row.getOffsetDateTime("tag_created_ts"),
				modifiedTs = row.getOffsetDateTime("tag_modified_ts")
			)
		}
	}
}
