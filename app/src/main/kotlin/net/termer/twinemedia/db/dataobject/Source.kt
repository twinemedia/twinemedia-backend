package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.source.FileSource
import java.time.OffsetDateTime

/**
 * Data class for a file source
 * @since 1.5.0
 */
class Source(
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
	 * The file source's configuration (different structure for each type, validate against the appropriate [FileSource] implementation)
	 * @since 1.5.0
	 */
	val config: JsonObject,

	/**
	 * The file source creator's account ID
	 * @since 1.5.0
	 */
	val creator: Int,

	/**
	 * Whether the file source is available to be used by all users
	 * @since 1.5.0
	 */
	val isGlobal: Boolean,

	/**
	 * The file source's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The file source's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime,
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.5.0
		 */
		val MAPPER = RowMapper { row ->
			Source(
				id = row.getInteger("id"),
				type = row.getString("source_type"),
				name = row.getString("source_name"),
				config = row.getJsonObject("source_config"),
				creator = row.getInteger("source_creator"),
				isGlobal = row.getBoolean("source_global"),
				createdTs = row.getOffsetDateTime("source_created_ts"),
				modifiedTs = row.getOffsetDateTime("source_modified_ts")
			)
		}
	}
}