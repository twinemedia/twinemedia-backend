package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for a media file
 * @param internalId The media file's internal ID, the one not exposed through the API
 * @param id The media file's alphanumeric ID
 * @param name The media file's user-defined name
 * @param filename The media file's filename (different from the user-defined name)
 * @param size The media file's size in bytes
 * @param mime The media file's MIME type
 * @param tags The media file's tags
 * @param key The media file's actual name on disk
 * @param createdOn The media file's creation time
 * @param modifiedOn The media file's last modified time
 * @param description The media file's description
 * @param meta Additional metadata for this media file (such as video/audio bitrate, resolution, etc) in JSON format
 * @param creator The media file creator's ID
 * @param parent The media file's parent media ID, of null if not a child
 * @param hash The media file's hash
 * @param hasThumbnail Whether the media file has a thumbnail
 * @param thumbnailFile The media file's thumbnail filename, or null if the media file has no thumbnail
 * @param isProcessing Whether the media file is currently processing
 * @param processError The error that caused the media file's processing to fail, or null if no error has occurred
 * @since 1.4.0
 */
@DataObject
class Media(
		/**
		 * The media file's internal ID, the one not exposed through the API
		 * @since 1.4.0
		 */
		val internalId: Int,
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
		 * The media file's media source file key
		 * @since 1.4.0
		 */
		val key: String,
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
		 * The media file's description
		 * @since 1.4.0
		 */
		val description: String?,
		/**
		 * Additional metadata for this media file (such as video/audio bitrate, resolution, etc) in JSON format
		 * @since 1.4.0
		 */
		val meta: JsonObject,
		/**
		 * The media file creator's ID
		 * @since 1.4.0
		 */
		val creator: Int,
		/**
		 * The media file's parent media ID, of null if not a child
		 * @since 1.4.0
		 */
		val parent: Int?,
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
		 * The media file's thumbnail filename on disk, or null if the media file has no thumbnail
		 * @since 1.4.0
		 */
		val thumbnailFile: String?,
		/**
		 * Whether the media file is currently processing
		 * @since 1.4.0
		 */
		val isProcessing: Boolean,
		/**
		 * The error that caused the media file's processing to fail, or null if no error has occurred
		 * @since 1.4.0
		 */
		val processError: String?,
		/**
		 * The media source's ID
		 * @since 1.5.0
		 */
		val source: Int
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<Media> { row ->
			Media(
					internalId = row.getInteger("id"),
					id = row.getString("media_id"),
					name = row.getString("media_name"),
					filename = row.getString("media_filename"),
					size = row.getLong("media_size"),
					mime = row.getString("media_mime"),
					tags = row.getJsonArray("media_tags").toStringArray(),
					key = row.getString("media_file"),
					createdOn = row.getOffsetDateTime("media_created_on"),
					modifiedOn = row.getOffsetDateTime("media_modified_on"),
					description = row.getString("media_description"),
					meta = row.getJsonObject("media_meta"),
					creator = row.getInteger("media_creator"),
					parent = row.getInteger("media_parent"),
					hash = row.getString("media_file_hash"),
					hasThumbnail = row.getBoolean("media_thumbnail"),
					thumbnailFile = row.getString("media_thumbnail_file"),
					isProcessing = row.getBoolean("media_processing"),
					processError = row.getString("media_process_error"),
					source = row.getInteger("media_source")
			)
		}
	}
}