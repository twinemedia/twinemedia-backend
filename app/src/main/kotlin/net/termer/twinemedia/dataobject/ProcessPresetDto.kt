package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * DTO for a media process preset
 * @since 2.0.0
 */
class ProcessPresetDto(
	override val internalId: Int,

	/**
	 * The preset's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

	/**
	 * The preset's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The MIME type (supports asterisk wildcards) that this preset applies to
	 * @since 2.0.0
	 */
	val mime: String,

	/**
	 * The preset's settings
	 * @since 2.0.0
	 */
	val settings: JsonObject,

	/**
	 * The preset's output file extension
	 * @since 2.0.0
	 */
	val extension: String,

	/**
	 * The preset's creator
	 * @since 2.0.0
	 */
	val creator: RecordCreatorDto,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): JsonSerializable(), StandardRow {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"mime" to mime,
		"settings" to settings,
		"creator" to creator.toJson(),
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = ProcessPresetDto(
			internalId = row.getInteger("id"),
			id = row.getString("preset_id"),
			name = row.getString("preset_name"),
			mime = row.getString("preset_mime"),
			settings = row.getJsonObject("preset_settings"),
			extension = row.getString("preset_extension"),
			creator = RecordCreatorDto(
				id = row.getString("preset_creator_id"),
				name = row.getString("preset_creator_name")
			),
			createdTs = row.getOffsetDateTime("preset_created_ts"),
			modifiedTs = row.getOffsetDateTime("preset_modified_ts")
		)
	}
}