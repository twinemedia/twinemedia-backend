package net.termer.twinemedia.dataobject

import io.vertx.sqlclient.Row
import java.time.OffsetDateTime

/**
 * Data class for a tag
 * @since 2.0.0
 */
class TagRow(
	/**
	 * The tag's internal sequential ID
	 * @since 2.0.0
	 */
	val internalId: Int,

	/**
	 * The tag's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

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
	 * The tag creator's account ID, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creatorId: Int?,

	/**
	 * The number of files using the tag
	 * @since 2.0.0
	 */
	val fileCount: Int,

	/**
	 * The tag's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The tag's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
) {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) {
			TagRow(
				internalId = row.getInteger("id"),
				id = row.getString("tag_id"),
				name = row.getString("tag_name"),
				description = row.getString("tag_description"),
				creatorId = row.getInteger("tag_creator"),
				fileCount = row.getInteger("tag_file_count"),
				createdTs = row.getOffsetDateTime("tag_created_ts"),
				modifiedTs = row.getOffsetDateTime("tag_modified_ts")
			)
		}
	}
}