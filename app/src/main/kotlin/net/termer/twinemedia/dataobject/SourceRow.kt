package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import net.termer.twinemedia.source.FileSource
import java.time.OffsetDateTime

/**
 * Data class for a file source
 * @since 2.0.0
 */
class SourceRow(
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
	 * The file source's configuration (different structure for each type, validate against the appropriate [FileSource] implementation)
	 * @since 2.0.0
	 */
	val config: JsonObject,

	/**
	 * The file source creator's account internal ID
	 * @since 2.0.0
	 */
	val creatorInternalId: Int?,

	/**
	 * Whether the file source is available to be used by all users
	 * @since 2.0.0
	 */
	val isGlobal: Boolean,

	/**
	 * The number of files associated with the file source
	 * @since 2.0.0
	 */
	val fileCount: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime,
): StandardRow {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = SourceRow(
			internalId = row.getInteger("id"),
			id = row.getString("source_id"),
			type = row.getString("source_type"),
			name = row.getString("source_name"),
			config = row.getJsonObject("source_config"),
			creatorInternalId = row.getInteger("source_creator"),
			isGlobal = row.getBoolean("source_global"),
			fileCount = row.getInteger("source_file_count"),
			createdTs = row.getOffsetDateTime("source_created_ts"),
			modifiedTs = row.getOffsetDateTime("source_modified_ts")
		)
	}
}