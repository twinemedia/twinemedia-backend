package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.containsColumn
import java.time.OffsetDateTime

/**
 * Data class for a media source
 * @param id The media source's internal ID
 * @param type The media source's type
 * @param name The media source's name
 * @param config The media source's configuration
 * @param creator The media source creator's ID
 * @param global Whether the media source is available to all users
 * @since 1.5.0
 */
@DataObject
class SourceInfo(
		/**
		 * The media source's internal ID
		 * @since 1.5.0
		 */
		val id: Int,
		/**
		 * The media source's type
		 * @since 1.5.0
		 */
		val type: String,
		/**
		 * The media source's name
		 * @since 1.5.0
		 */
		val name: String,
		/**
		 * The media source creator's ID
		 * @since 1.5.0
		 */
		val creator: Int,
		/**
		 * The media source creator's name, or null if it no longer exists
		 * @since 1.5.0
		 */
		val creatorName: String?,
		/**
		 * Whether the media source is available to all users
		 * @since 1.5.0
		 */
		val global: Boolean,
		/**
		 * The media source's creation time
		 * @since 1.5.0
		 */
		val createdOn: OffsetDateTime,
		/**
		 * Whether this media source info object includes a config
		 * @since 1.5.0
		 */
		val hasConfig: Boolean,
		/**
		 * The media source's config
		 * @since 1.5.0
		 */
		val config: JsonObject?,
		/**
		 * The media source's media file count
		 * @since 1.5.0
		 */
		val mediaCount: Int
): SerializableDataObject {
	override fun toJson(): JsonObject {
		val json = json {obj(
				"id" to id,
				"type" to type,
				"name" to name,
				"creator" to creator,
				"creator_name" to creatorName,
				"global" to global,
				"created_on" to createdOn.toString(),
				"media_count" to mediaCount
		)}

		if(hasConfig)
			json.put("config", config)

		return json
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.5.0
		 */
		val MAPPER = RowMapper<SourceInfo> { row ->
			SourceInfo(
					id = row.getInteger("id"),
					type = row.getString("type"),
					name = row.getString("name"),
					creator = row.getInteger("creator"),
					creatorName = row.getString("creator_name"),
					global = row.getBoolean("global"),
					createdOn = row.getOffsetDateTime("created_on"),
					hasConfig = row.containsColumn("config"),
					config = if(row.containsColumn("config")) row.getJsonObject("config") else null,
					mediaCount = row.getInteger("media_count")
			)
		}
	}
}