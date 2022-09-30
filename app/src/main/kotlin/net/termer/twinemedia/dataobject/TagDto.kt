package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.hasCol
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * DTO for a tag
 * @since 2.0.0
 */
class TagDto(
	/**
	 * The tag's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

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
	 * The tag creator's account, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creator: RecordCreatorDto?,

	/**
	 * The number of files using the tag
	 * @since 2.0.0
	 */
	val fileCount: Int,

	/**
	 * The tag's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The tag's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
): JsonSerializable() {
	override fun toJson(): JsonObject = jsonObjectOf(
		"id" to id,
		"name" to name,
		"description" to description,
		"creator" to creator?.toJson(),
		"fileCount" to fileCount,
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 2.0.0
		 */
		val MAPPER = RowMapper { row ->
			val tagCreatorId = row.getString("tag_creator_id")

			TagDto(
				id = row.getString("tag_id"),
				name = row.getString("tag_name"),
				description = if(row.hasCol("file_description")) row.getString("tag_description") else null,
				creator = if(tagCreatorId == null) null else RecordCreatorDto(
					id = tagCreatorId,
					name = row.getString("tag_creator_name")
				),
				fileCount = row.getInteger("tag_file_count"),
				createdTs = row.getOffsetDateTime("tag_created_ts"),
				modifiedTs = row.getOffsetDateTime("tag_modified_ts")
			)
		}
	}
}