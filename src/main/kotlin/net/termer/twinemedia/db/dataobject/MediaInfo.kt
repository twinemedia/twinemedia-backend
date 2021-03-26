package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toJsonArray
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for a media file's info
 * @param id The media file's alphanumeric ID
 * @param name The media file's user-defined name
 * @param filename The media file's filename (different from the user-defined name)
 * @param size The media file's size in bytes
 * @param mime The media file's MIME type
 * @param createdOn The media file's creation time
 * @param modifiedOn The media file's last modified time
 * @param creator The media file creator's ID
 * @param creatorName The media file creator's name (null if the account doesn't exist)
 * @param hash The media file's hash
 * @param hasThumbnail Whether the media file has a thumbnail
 * @param isProcessing Whether the media file is currently processing
 * @param processError The error that caused the media file's processing to fail, or null if no error has occurred
 * @since 1.4.0
 */
@DataObject
class MediaInfo(
		/**
		 * The media file's internal ID, the one not exposed through the API
		 * @since 1.4.0
		 */
		val internalId: Int,
		/**
		 * The internal ID of the media file's parent, or null if there is none
		 * @since 1.4.0
		 */
		val internalParent: Int?,
		/**
		 * The media file's alphanumeric ID
		 * @since 1.4.0
		 */
		val id: String,
		/**
		 * The media file's user-defined name
		 * @since 1.4.0
		 */
		val name: String?,
		/**
		 * The media file's filename (different from the user-defined name)
		 * @since 1.4.0
		 */
		val filename: String,
		/**
		 * The media file's size in bytes
		 * @since 1.4.0
		 */
		val size: Long,
		/**
		 * The media file's MIME type
		 * @since 1.4.0
		 */
		val mime: String,
		/**
		 * The media file's tags
		 * @since 1.4.0
		 */
		val tags: Array<String>,
		/**
		 * The media file's creation time
		 * @since 1.4.0
		 */
		val createdOn: OffsetDateTime,
		/**
		 * The media file's last modified time
		 * @since 1.4.0
		 */
		val modifiedOn: OffsetDateTime,
		/**
		 * The media file creator's ID
		 * @since 1.4.0
		 */
		val creator: Int,
		/**
		 * The media file creator's name (null if the account doesn't exist)
		 * @since 1.4.0
		 */
		val creatorName: String?,
		/**
		 * The media file's hash
		 * @since 1.4.0
		 */
		val hash: String,
		/**
		 * Whether the media file has a thumbnail
		 * @since 1.4.0
		 */
		val hasThumbnail: Boolean,
		/**
		 * Whether the media file is currently processing
		 * @since 1.4.0
		 */
		val isProcessing: Boolean,
		/**
		 * The error that caused the media file's processing to fail, or null if no error has occurred
		 * @since 1.4.0
		 */
		val processError: String?
) {
	/**
	 * Returns a JSON representation of the media's info
	 * @return A JSON representation of the media's info
	 * @since 1.4.0
	 */
	fun toJson() = json {
		obj(
				"id" to id,
				"name" to name,
				"filename" to filename,
				"creator" to creator,
				"size" to size,
				"mime" to mime,
				"created_on" to createdOn.toString(),
				"modified_on" to modifiedOn.toString(),
				"creator" to creator,
				"creator_name" to creatorName,
				"file_hash" to hash,
				"thumbnail" to hasThumbnail,
				"tags" to tags.toJsonArray(),
				"processing" to isProcessing,
				"process_error" to processError
		)
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<MediaInfo> { row ->
			MediaInfo(
					internalId = row.getInteger("internal_id"),
					internalParent = row.getInteger("internal_parent"),
					id = row.getString("id"),
					name = row.getString("name"),
					filename = row.getString("filename"),
					size = row.getLong("size"),
					mime = row.getString("mime"),
					tags = row.getJsonArray("tags").toStringArray(),
					createdOn = row.getOffsetDateTime("created_on"),
					modifiedOn = row.getOffsetDateTime("modified_on"),
					creator = row.getInteger("creator"),
					creatorName = row.getString("creator_name"),
					hash = row.getString("file_hash"),
					hasThumbnail = row.getBoolean("thumbnail"),
					isProcessing = row.getBoolean("processing"),
					processError = row.getString("process_error")
			)
		}
	}
}