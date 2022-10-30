package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime

/**
 * Data class for a media process preset
 * @since 2.0.0
 */
class ProcessPresetRow(
	override val internalId: Int,
	override val id: String,

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
	 * The preset creator's account internal ID
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
		fun fromRow(row: Row) = ProcessPresetRow(
			internalId = row.getInteger("id"),
			id = row.getString("preset_id"),
			name = row.getString("preset_name"),
			mime = row.getString("preset_mime"),
			settings = row.getJsonObject("preset_settings"),
			creatorInternalId = row.getInteger("preset_creator"),
			createdTs = row.getOffsetDateTime("preset_created_ts"),
			modifiedTs = row.getOffsetDateTime("preset_modified_ts")
		)
	}
}