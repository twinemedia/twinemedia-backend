package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.db.hasCol
import java.time.OffsetDateTime

/**
 * DTO for a file source
 * @since 2.0.0
 */
class SourceDto(
	override val internalId: Int,
	override val id: String,

	/**
	 * The file source's type
	 * @since 2.0.0
	 */
	val type: String,

	/**
	 * The file source's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The file source's creator, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creator: RecordCreatorDto?,

	/**
	 * Whether the file source is available to be used by all accounts
	 * @since 2.0.0
	 */
	val isGlobal: Boolean,

	/**
	 * The file source's config, or null if it was not included
	 * @since 2.0.0
	 */
	val config: JsonObject?,

	/**
	 * The number of files associated with the file source
	 * @since 2.0.0
	 */
	val fileCount: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): JsonSerializable(), StandardRow {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"type" to type,
		"name" to name,
		"creator" to creator?.toJson(),
		"isGlobal" to isGlobal,
		"config" to config,
		"fileCount" to fileCount,
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString(),
	)

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row): SourceDto {
			val sourceCreatorId = row.getString("source_creator_id")

			return SourceDto(
				internalId = row.getInteger("id"),
				id = row.getString("source_id"),
				type = row.getString("source_type"),
				name = row.getString("source_name"),
				creator = if(sourceCreatorId == null) null else RecordCreatorDto(
					id = sourceCreatorId,
					name = row.getString("source_creator_name")
				),
				isGlobal = row.getBoolean("source_global"),
				config = if(row.hasCol("source_config")) row.getJsonObject("source_config") else null,
				fileCount = row.getInteger("source_file_count"),
				createdTs = row.getOffsetDateTime("source_created_ts"),
				modifiedTs = row.getOffsetDateTime("source_modified_ts")
			)
		}
	}
}