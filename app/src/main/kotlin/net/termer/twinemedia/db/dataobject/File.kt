package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.templates.RowMapper
import java.time.OffsetDateTime

/**
 * Data class for a file
 * @since 1.4.0
 */
class File(
		/**
		 * The file's internal sequential ID
		 * @since 1.4.0
		 */
		val internalId: Int,

		/**
		 * The file's alphanumeric ID
		 * @since 1.4.0
		 */
		val id: String,

		/**
		 * The file's user-defined name
		 * @since 1.4.0
		 */
		val name: String?,

		/**
		 * The file's filename (different from the user-defined name)
		 * @since 1.4.0
		 */
		val filename: String,

		/**
		 * The file's size in bytes
		 * @since 1.4.0
		 */
		val size: Long,

		/**
		 * The file's MIME type
		 * @since 1.4.0
		 */
		val mime: String,

		/**
		 * The file's key on the file source on which it is stored
		 * @since 1.4.0
		 */
		val key: String,

		/**
		 * The file's description, or null if it has none
		 * @since 1.4.0
		 */
		val description: String?,

		/**
		 * Additional metadata for this file (such as video/audio bitrate, resolution, etc.) in JSON format
		 * @since 1.4.0
		 */
		val meta: JsonObject,

		/**
		 * The file creator's account ID
		 * @since 1.4.0
		 */
		val creator: Int,

		/**
		 * The file's parent ID, of null if not a child
		 * @since 1.4.0
		 */
		val parent: Int?,

		/**
		 * The file's hash
		 * @since 1.4.0
		 */
		val hash: String,

		/**
		 * The file's thumbnail key, or null if the file has no thumbnail
		 * @since 1.4.0
		 */
		val thumbnailKey: String?,

		/**
		 * Whether the file is currently processing
		 * @since 1.4.0
		 */
		val isProcessing: Boolean,

		/**
		 * The error that caused the file's processing to fail, or null if no error has occurred
		 * @since 1.4.0
		 */
		val processError: String?,

		/**
		 * The file source's ID
		 * @since 1.5.0
		 */
		val source: Int,

		/**
		 * The file's creation timestamp
		 * @since 1.4.0
		 */
		val createdTs: OffsetDateTime,

		/**
		 * The file's last modified timestamp
		 * @since 1.4.0
		 */
		val modifiedTs: OffsetDateTime
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			File(
				internalId = row.getInteger("id"),
				id = row.getString("file_id"),
				name = row.getString("file_name"),
				filename = row.getString("file_filename"),
				size = row.getLong("file_size"),
				mime = row.getString("file_mime"),
				key = row.getString("file_key"),
				description = row.getString("file_description"),
				meta = row.getJsonObject("file_meta"),
				creator = row.getInteger("file_creator"),
				parent = row.getInteger("file_parent"),
				hash = row.getString("file_hash"),
				thumbnailKey = row.getString("file_thumbnail_key"),
				isProcessing = row.getBoolean("file_processing"),
				processError = row.getString("file_process_error"),
				source = row.getInteger("file_source"),
				createdTs = row.getOffsetDateTime("file_created_ts"),
				modifiedTs = row.getOffsetDateTime("file_modified_ts")
			)
		}
	}

	/**
	 * Whether the file has a thumbnail
	 * @since 2.0.0
	 */
	val hasThumbnail: Boolean
		get() = thumbnailKey != null

	/**
	 * Whether the file has a description
	 * @since 2.0.0
	 */
	val hasDescription: Boolean
		get() = description != null
}