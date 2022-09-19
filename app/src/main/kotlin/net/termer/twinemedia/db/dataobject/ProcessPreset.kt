package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.templates.RowMapper
import java.time.OffsetDateTime

/**
 * Data class for a media process preset
 * @since 1.4.0
 */
class ProcessPreset(
		/**
		 * The preset's ID
		 * @since 1.4.0
		 */
		val id: Int,

		/**
		 * The MIME type (supports asterisk wildcards) that this preset applies to
		 * @since 1.4.0
		 */
		val mime: String,

		/**
		 * The preset's settings
		 * @since 1.4.0
		 */
		val settings: JsonObject,

		/**
		 * The preset creator's account ID
		 * @since 1.4.0
		 */
		val creator: Int,

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
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			ProcessPreset(
				id = row.getInteger("id"),
				mime = row.getString("preset_mime"),
				settings = row.getJsonObject("preset_settings"),
				creator = row.getInteger("preset_creator"),
				createdTs = row.getOffsetDateTime("preset_created_ts"),
				modifiedTs = row.getOffsetDateTime("preset_modified_ts")
			)
		}
	}
}