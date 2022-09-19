package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.hasCol
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.toIntArray
import net.termer.twinemedia.util.toJsonArray
import java.time.OffsetDateTime

/**
 * Data class for a file's info
 * @since 1.4.0
 */
class FileInfo(
	/**
	 * The file's internal sequential ID
	 * @since 1.4.0
	 */
	val internalId: Int,

	/**
	 * The internal ID of the file's parent, or if not a child
	 * @since 2.0.0
	 */
	val parentInternalId: Int?,

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
	 * An array containing the file's tag IDs
	 * @since 2.0.0
	 */
	val tagIds: Array<Int>,

	/**
	 * The file creator's account ID
	 * @since 1.4.0
	 */
	val creator: Int,

	/**
	 * The file creator's name (null if the account doesn't exist)
	 * @since 1.4.0
	 */
	val creatorName: String?,

	/**
	 * The file's hash
	 * @since 1.4.0
	 */
	val hash: String,

	/**
	 * Whether the file has a thumbnail
	 * @since 1.4.0
	 */
	val hasThumbnail: Boolean,

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
	 * The file's description, or null if it has none (or if it is not included; check [includesDescription] to find out whether the file's description was included)
	 * @since 1.4.2
	 */
	val description: String?,

	/**
	 * Whether this object includes the file's description
	 * @since 2.0.0
	 */
	val includesDescription: Boolean,

	/**
	 * The file source's ID
	 * @since 1.5.0
	 */
	val source: Int,

	/**
	 * The file source's type
	 * @since 1.5.0
	 */
	val sourceType: String?,

	/**
	 * The file source's name
	 * @since 1.5.0
	 */
	val sourceName: String?,

	/**
	 * The file's creation timestamp
	 * @since 1.4.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The file's last modified timestamp
	 * @since 1.4.0
	 */
	val modifiedTs: OffsetDateTime,
): JsonSerializable {
	override fun toJson(): JsonObject {
		val json = jsonObjectOf(
			"id" to id,
			"name" to name,
			"filename" to filename,
			"creator" to creator,
			"size" to size,
			"mime" to mime,
			"creator" to creator,
			"creator_name" to creatorName,
			"file_hash" to hash,
			"thumbnail" to hasThumbnail,
			"tagIds" to tagIds.toJsonArray(),
			"processing" to isProcessing,
			"process_error" to processError,
			"source" to source,
			"source_type" to sourceType,
			"source_name" to sourceName,
			"created_ts" to createdTs.toString(),
			"modified_ts" to modifiedTs.toString()
		)

		if(includesDescription)
			json.put("description", description)

		return json
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			val includesDesc = row.hasCol("description")

			FileInfo(
				internalId = row.getInteger("internal_id"),
				parentInternalId = row.getInteger("parent_internal_id"),
				id = row.getString("id"),
				name = row.getString("name"),
				filename = row.getString("filename"),
				size = row.getLong("size"),
				mime = row.getString("mime"),
				tagIds = row.getJsonArray("tag_ids").toIntArray(),
				creator = row.getInteger("creator"),
				creatorName = row.getString("creator_name"),
				hash = row.getString("hash"),
				hasThumbnail = row.getBoolean("has_thumbnail"),
				isProcessing = row.getBoolean("processing"),
				processError = row.getString("process_error"),
				includesDescription = includesDesc,
				description = if(includesDesc) row.getString("description") else null,
				source = row.getInteger("source"),
				sourceType = row.getString("source_type"),
				sourceName = row.getString("source_name"),
				createdTs = row.getOffsetDateTime("created_ts"),
				modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}