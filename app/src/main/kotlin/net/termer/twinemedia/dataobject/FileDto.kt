package net.termer.twinemedia.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.db.hasCol
import net.termer.twinemedia.util.toJsonArray
import java.time.OffsetDateTime

/**
 * DTO for a file
 * @since 2.0.0
 */
class FileDto(
	/**
	 * TODO Is this still needed?
	 * The file's internal sequential ID.
	 * Not exposed in JSON.
	 * @since 2.0.0
	 */
	val internalId: Int,

	/**
	 * TODO Is this still needed?
	 * The internal ID of the file's parent, or null if not a child.
	 * Not exposed in JSON.
	 * @since 2.0.0
	 */
	val parentInternalId: Int?,

	/**
	 * The file's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

	/**
	 * The file's title
	 * @since 2.0.0
	 */
	val title: String,

	/**
	 * The file's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The file's description, or null if it was not included
	 * @since 2.0.0
	 */
	val description: String?,

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
	 * Additional metadata for the file (such as video/audio bitrate, resolution, etc.) in JSON format, or null if it was not included
	 * @since 2.0.0
	 */
	val meta: JsonObject?,

	/**
	 * The file's hash
	 * @since 2.0.0
	 */
	val hash: String,

	/**
	 * Whether the file has a thumbnail
	 * @since 2.0.0
	 */
	val hasThumbnail: Boolean,

	/**
	 * The file's creator, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creator: RecordCreatorDto?,

	/**
	 * The number of tags the file has
	 * @since 2.0.0
	 */
	val tagCount: Int,

	/**
	 * An array containing the file's tags, or null if they were not fetched
	 * @since 2.0.0
	 */
	var tags: Array<TagDto>? = null,

	/**
	 * The file's parent, or null if the file has no parent or if it was not fetched
	 * @since 2.0.0
	 */
	var parent: FileDto? = null,

	/**
	 * The number of children the file has
	 * @since 2.0.0
	 */
	val childCount: Int,

	/**
	 * An array containing the file's children, or null if they were not fetched
	 * @since 2.0.0
	 */
	var children: Array<FileDto>? = null,

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
	 * The file's source
	 * @since 2.0.0
	 */
	val source: RecordSourceDto,

	/**
	 * The file's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The file's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime,
): JsonSerializable() {
	override fun toJson(): JsonObject = jsonObjectOf(
		"id" to id,
		"title" to title,
		"name" to name,
		"description" to description,
		"size" to size,
		"mime" to mime,
		"meta" to meta,
		"hash" to hash,
		"hasThumbnail" to hasThumbnail,
		"creator" to creator?.toJson(),
		"tagCount" to tagCount,
		"tags" to tags?.toJsonArray(),
		"parent" to parent?.toJson(),
		"childCount" to childCount,
		"children" to children?.map { toJson() },
		"isProcessing" to isProcessing,
		"processError" to processError,
		"source" to source.toJson(),
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row): FileDto {
			val fileCreatorId = row.getString("file_creator_id")

			return FileDto(
				internalId = row.getInteger("internal_id"), // TODO Still needed?
				parentInternalId = row.getInteger("parent_internal_id"), // TODO Still needed?
				id = row.getString("file_id"),
				title = row.getString("file_title"),
				name = row.getString("file_name"),
				description = if(row.hasCol("file_description")) row.getString("file_description") else null,
				size = row.getLong("file_size"),
				mime = row.getString("file_mime"),
				meta = if(row.hasCol("file_meta")) row.getJsonObject("file_meta") else null,
				hash = row.getString("file_hash"),
				hasThumbnail = row.getBoolean("file_has_thumbnail"),
				creator = if(fileCreatorId == null) null else RecordCreatorDto(
					id = fileCreatorId,
					name = row.getString("file_creator_name")
				),
				tagCount = row.getInteger("file_tag_count"),
				childCount = row.getInteger("file_child_count"),
				isProcessing = row.getBoolean("file_processing"),
				processError = row.getString("file_process_error"),
				source = RecordSourceDto(
					id = row.getString("file_source_id"),
					name = row.getString("file_source_name"),
					type = row.getString("file_source_type")
				),
				createdTs = row.getOffsetDateTime("created_ts"),
				modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}