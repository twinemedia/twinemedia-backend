package net.termer.twinemedia.dataobject

import io.vertx.sqlclient.Row
import java.time.OffsetDateTime

/**
 * Data class for a tag
 * @since 2.0.0
 */
class TagRow(
	override val internalId: Int,
	override val id: String,

	/**
	 * The tag's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The tag's description
	 * @since 2.0.0
	 */
	val description: String,

	/**
	 * The tag creator's account internal ID, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creatorInternalId: Int?,

	/**
	 * The number of files using the tag
	 * @since 2.0.0
	 */
	val fileCount: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): StandardRow {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = TagRow(
			internalId = row.getInteger("id"),
			id = row.getString("tag_id"),
			name = row.getString("tag_name"),
			description = row.getString("tag_description"),
			creatorInternalId = row.getInteger("tag_creator"),
			fileCount = row.getInteger("tag_file_count"),
			createdTs = row.getOffsetDateTime("tag_created_ts"),
			modifiedTs = row.getOffsetDateTime("tag_modified_ts")
		)
	}
}