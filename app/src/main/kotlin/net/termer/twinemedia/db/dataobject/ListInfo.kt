package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.hasCol
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.util.*
import java.time.OffsetDateTime

/**
 * Data class for a list's info
 * @since 1.4.0
 */
class ListInfo(
	/**
	 * The list's alphanumeric ID
	 * @since 1.4.0
	 */
	val id: String,

	/**
	 * The list's name
	 * @since 1.4.0
	 */
	val name: String,

	/**
	 * The list's description
	 * @since 1.4.0
	 */
	val description: String?,

	/**
	 * The list creator's account ID
	 * @since 1.4.0
	 */
	val creator: Int,

	/**
	 * The list creator's name, or null if the account doesn't exist
	 * @since 1.4.0
	 */
	val creatorName: String?,

	/**
	 * The list's type
	 * @since 1.4.0
	 */
	val type: ListType,

	/**
	 * The list's visibility
	 * @since 1.4.0
	 */
	val visibility: ListVisibility,

	/**
	 * The list's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The list's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime,

	/**
	 * The tag IDs that files must have to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceTagIds: Array<Int>?,

	/**
	 * The tag IDs that files must not have to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceExcludeTagIds: Array<Int>?,
	
	/**
	 * The MIME type files must have to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 1.4.0
	 */
	val sourceMime: String?,
	
	/**
	 * The time files must have been uploaded before to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 1.4.0
	 */
	val sourceCreatedBefore: OffsetDateTime?,
	
	/**
	 * The time files must have been uploaded after to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 1.4.0
	 */
	val sourceCreatedAfter: OffsetDateTime?,
	
	/**
	 * Whether files by all users should be shown in list, not just by the list creator.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED].
	 * @since 1.4.2
	 */
	val showAllUserFiles: Boolean,
	
	/**
	 * The amount of items this list has.
	 * Only applies to lists with type [ListType.STANDARD], will be -1 for other types.
	 * @since 1.4.0
	 */
	val itemCount: Int,
	
	/**
	 * Whether this list contains a file that was specified in a query.
	 * Will be null if none was specified.
	 * @since 1.4.0
	 */
	val containsFile: Boolean?
): JsonSerializable {
	override fun toJson(): JsonObject {
		val json = jsonObjectOf(
			"id" to id,
			"name" to name,
			"description" to description,
			"creator" to creator,
			"creator_name" to creatorName,
			"type" to type.ordinal,
			"visibility" to visibility.ordinal,
			"source_tag_ids" to sourceTagIds?.toJsonArray(),
			"source_exclude_tag_ids" to sourceExcludeTagIds?.toJsonArray(),
			"source_created_before" to sourceCreatedBefore?.toString(),
			"source_created_after" to sourceCreatedAfter?.toString(),
			"show_all_user_files" to showAllUserFiles,
			"source_mime" to sourceMime,
			"item_count" to itemCount,
			"created_ts" to createdTs.toString(),
			"modified_ts" to modifiedTs.toString()
		)

		if(containsFile != null)
			json.put("contains_file", containsFile)

		return json
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			ListInfo(
				id = row.getString("id"),
				name = row.getString("name"),
				description = row.getString("description"),
				creator = row.getInteger("creator"),
				creatorName = row.getString("creator_name"),
				// Allow throwing of NPE here because an invalid type should never have been in the database in the first place
				type = intToListType(row.getInteger("type"))!!,
				// Same rationale for this column as well
				visibility = intToListVisibility(row.getInteger("visibility"))!!,
				sourceTagIds = row.getArrayOfIntegers("source_tag_ids"),
				sourceExcludeTagIds = row.getArrayOfIntegers("source_exclude_tag_ids"),
				sourceMime = row.getString("source_mime"),
				sourceCreatedBefore = row.getOffsetDateTime("source_created_before"),
				sourceCreatedAfter = row.getOffsetDateTime("source_created_after"),
				showAllUserFiles = row.getBoolean("show_all_user_files"),
				itemCount = row.getInteger("item_count"),
				containsFile = if(row.hasCol("contains_file")) row.getBoolean("contains_file") else null,
				createdTs = row.getOffsetDateTime("created_ts"),
				modifiedTs = row.getOffsetDateTime("modified_ts")
			)
		}
	}
}