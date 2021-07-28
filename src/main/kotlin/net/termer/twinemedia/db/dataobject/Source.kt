package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.templates.RowMapper
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
class Source(
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
		 * The media source's configuration (different structure for each type, validate against the appropriate MediaSource implementation)
		 * @since 1.5.0
		 */
		val config: JsonObject,
		/**
		 * The media source creator's ID
		 * @since 1.5.0
		 */
		val creator: Int,
		/**
		 * Whether the media source is available to all users
		 * @since 1.5.0
		 */
		val global: Boolean,
		/**
		 * The media source's creation time
		 * @since 1.5.0
		 */
		val createdOn: OffsetDateTime
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.5.0
		 */
		val MAPPER = RowMapper<Source> { row ->
			Source(
					id = row.getInteger("id"),
					type = row.getString("source_type"),
					name = row.getString("source_name"),
					config = row.getJsonObject("source_config"),
					creator = row.getInteger("source_creator"),
					global = row.getBoolean("source_global"),
					createdOn = row.getOffsetDateTime("source_created_on")
			)
		}
	}
}