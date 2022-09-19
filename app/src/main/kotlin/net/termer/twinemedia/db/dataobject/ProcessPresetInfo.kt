package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * Data class for a media process preset's info
 * @since 1.4.0
 */
class ProcessPresetInfo(
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
		 * The preset's output file extension
		 * @since 1.4.0
		 */
		val extension: String,

		/**
		 * The preset creator's account ID
		 * @since 1.4.0
		 */
		val creator: Int,

		/**
		 * The preset creator's name (null if the account doesn't exist)
		 * @since 1.4.0
		 */
		val creatorName: String?,

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
): JsonSerializable {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"mime" to mime,
		"settings" to settings,
		"creator" to creator,
		"creator_name" to creatorName,
		"created_ts" to createdTs.toString(),
		"modified_ts" to modifiedTs.toString()
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			ProcessPresetInfo(
					id = row.getInteger("id"),
					mime = row.getString("mime"),
					settings = row.getJsonObject("settings"),
					extension = row.getString("extension"),
					creator = row.getInteger("creator"),
					creatorName = row.getString("creator_name"),
					createdTs = row.getOffsetDateTime("created_ts"),
					modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}