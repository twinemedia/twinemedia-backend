package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * DTO for a media process preset
 * @since 2.0.0
 */
class ProcessPresetDto(
	/**
	 * The preset's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

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

	/**
	 * The preset's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The preset's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
): JsonSerializable() {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"mime" to mime,
		"settings" to settings,
		"creator" to creator.toJson(),
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 2.0.0
		 */
		val MAPPER = RowMapper { row ->
			ProcessPresetDto(
					id = row.getString("preset_id"),
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
}