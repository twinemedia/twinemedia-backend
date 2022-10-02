package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime

/**
 * Data class for a file
 * @since 2.0.0
 */
class FileRow(
	/**
	 * The file's internal sequential ID
	 * @since 2.0.0
	 */
	val internalId: Int,

	/**
	 * The file's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

	/**
	 * The file's title
	 * @since 2.0.0
	 */
	val title: String?,

	/**
	 * The file's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The file's size in bytes
	 * @since 2.0.0
	 */
	val size: Long,

	/**
	 * The file's MIME type
	 * @since 2.0.0
	 */
	val mime: String,

	/**
	 * The file's key on the file source on which it is stored
	 * @since 2.0.0
	 */
	val key: String,

	/**
	 * The file's description
	 * @since 2.0.0
	 */
	val description: String,

	/**
	 * Additional metadata for the file (such as video/audio bitrate, resolution, etc.) in JSON format
	 * @since 2.0.0
	 */
	val meta: JsonObject,

	/**
	 * The file creator's account ID, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creatorId: Int?,

	/**
	 * The file's parent ID, of null if not a child
	 * @since 2.0.0
	 */
	val parentId: Int?,

	/**
	 * The file's hash
	 * @since 2.0.0
	 */
	val hash: String,

	/**
	 * The file's thumbnail key, or null if the file has no thumbnail
	 * @since 2.0.0
	 */
	val thumbnailKey: String?,

	/**
	 * Whether the file is currently processing
	 * @since 2.0.0
	 */
	val isProcessing: Boolean,

	/**
	 * The error that caused the file's processing to fail, or null if no error has occurred
	 * @since 2.0.0
	 */
	val processError: String?,

	/**
	 * The file source's ID
	 * @since 2.0.0
	 */
	val sourceId: Int,

	/**
	 * The number of tags the file has
	 * @since 2.0.0
	 */
	val tagCount: Int,

	/**
	 * The number of child files the file has
	 * @since 2.0.0
	 */
	val childCount: Int,

	/**
	 * The file's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The file's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
) {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = FileRow(
			internalId = row.getInteger("id"),
			id = row.getString("file_id"),
			title = row.getString("file_name"),
			name = row.getString("file_filename"),
			size = row.getLong("file_size"),
			mime = row.getString("file_mime"),
			key = row.getString("file_key"),
			description = row.getString("file_description"),
			meta = row.getJsonObject("file_meta"),
			creatorId = row.getInteger("file_creator"),
			parentId = row.getInteger("file_parent"),
			hash = row.getString("file_hash"),
			thumbnailKey = row.getString("file_thumbnail_key"),
			isProcessing = row.getBoolean("file_processing"),
			processError = row.getString("file_process_error"),
			sourceId = row.getInteger("file_source"),
			tagCount = row.getInteger("file_tag_count"),
			childCount = row.getInteger("file_child_count"),
			createdTs = row.getOffsetDateTime("file_created_ts"),
			modifiedTs = row.getOffsetDateTime("file_modified_ts")
		)
	}

	/**
	 * Whether the file has a thumbnail
	 * @since 2.0.0
	 */
	val hasThumbnail: Boolean
		get() = thumbnailKey != null

	/**
	 * Whether the file is a child to another file
	 * @since 2.0.0
	 */
	val isChild: Boolean
		get() = parentId != null
}