package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.hasCol
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * Data class for a file source's info
 * @since 1.5.0
 */
class SourceInfo(
	/**
	 * The file source's internal sequential ID
	 * @since 1.5.0
	 */
	val id: Int,

	/**
	 * The file source's type
	 * @since 1.5.0
	 */
	val type: String,

	/**
	 * The file source's name
	 * @since 1.5.0
	 */
	val name: String,

	/**
	 * The file source creator's account ID
	 * @since 1.5.0
	 */
	val creator: Int,

	/**
	 * The file source creator's name, or null if it no longer exists
	 * @since 1.5.0
	 */
	val creatorName: String?,

	/**
	 * Whether the file source is available to be used by all users
	 * @since 1.5.0
	 */
	val global: Boolean,

	/**
	 * Whether this file source info object includes a config
	 * @since 1.5.0
	 */
	val includesConfig: Boolean,

	/**
	 * The file source's config
	 * @since 1.5.0
	 */
	val config: JsonObject?,

	/**
	 * The file source's file count
	 * @since 1.5.0
	 */
	val fileCount: Int,

	/**
	 * The file source's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The file source's last modified timestamp
	 */
	val modifiedTs: OffsetDateTime
): JsonSerializable {
	override fun toJson(): JsonObject {
		val json = jsonObjectOf(
			"id" to id,
			"type" to type,
			"name" to name,
			"creator" to creator,
			"creator_name" to creatorName,
			"global" to global,
			"file_count" to fileCount,
			"created_ts" to createdTs.toString(),
			"modified_ts" to modifiedTs.toString(),
		)

		if(includesConfig)
			json.put("config", config)

		return json
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.5.0
		 */
		val MAPPER = RowMapper { row ->
			SourceInfo(
				id = row.getInteger("id"),
				type = row.getString("type"),
				name = row.getString("name"),
				creator = row.getInteger("creator"),
				creatorName = row.getString("creator_name"),
				global = row.getBoolean("global"),
				includesConfig = row.hasCol("config"),
				config = if(row.hasCol("config")) row.getJsonObject("config") else null,
				fileCount = row.getInteger("file_count"),
				createdTs = row.getOffsetDateTime("created_ts"),
				modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}