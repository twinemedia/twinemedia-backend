package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.hasCol
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.util.*
import java.time.OffsetDateTime

/**
 * DTO for a list
 * @since 2.0.0
 */
class ListDto(
	/**
	 * The list's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

	/**
	 * The list's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The list's description
	 * @since 2.0.0
	 */
	val description: String?,

	/**
	 * The list's creator, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creator: RecordCreatorDto?,

	/**
	 * The list's type
	 * @since 2.0.0
	 */
	val type: ListType,

	/**
	 * The list's visibility
	 * @since 2.0.0
	 */
	val visibility: ListVisibility,

	/**
	 * The tags that files must have to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceTags: Array<String>?,

	/**
	 * The tags that files must NOT have to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceExcludeTags: Array<String>?,

	/**
	 * The MIME type files must have to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceMime: String?,

	/**
	 * The time files must have been uploaded before to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceCreatedBefore: OffsetDateTime?,

	/**
	 * The time files must have been uploaded after to be in this list.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], will be null for other types.
	 * @since 2.0.0
	 */
	val sourceCreatedAfter: OffsetDateTime?,

	/**
	 * Whether files by all users should be shown in list, not just by the list creator.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED]
	 * @since 2.0.0
	 */
	val showAllUserFiles: Boolean,

	/**
	 * The number of items in the list.
	 * Only applies to lists with type [ListType.STANDARD], otherwise it will be null.
	 * @since 2.0.0
	 */
	val itemCount: Int?,

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
	 * Whether this list contains a file that was specified in a query.
	 * Will be null if none was specified.
	 * @since 2.0.0
	 */
	val containsFile: Boolean?
): JsonSerializable() {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"description" to description,
		"creator" to creator?.toJson(),
		"type" to type.ordinal,
		"visibility" to visibility.ordinal,
		"sourceTag_ids" to sourceTags?.toJsonArray(),
		"sourceExclude_tag_ids" to sourceExcludeTags?.toJsonArray(),
		"sourceCreated_before" to sourceCreatedBefore?.toString(),
		"sourceCreated_after" to sourceCreatedAfter?.toString(),
		"showAllUserFiles" to showAllUserFiles,
		"sourceMime" to sourceMime,
		"itemCount" to itemCount,
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString(),
		"containsFile" to containsFile
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 2.0.0
		 */
		val MAPPER = RowMapper { row ->
			val listCreatorId = row.getString("list_creator_id")

			ListDto(
				id = row.getString("list_id"),
				name = row.getString("list_name"),
				description = row.getString("list_description"),
				creator = if(listCreatorId == null) null else RecordCreatorDto(
					id = listCreatorId,
					name = row.getString("list_creator_name")
				),
				// Allow throwing of NPE here because an invalid type should never have been in the database in the first place
				type = intToListType(row.getInteger("list_type"))!!,
				// Same rationale for this column as well
				visibility = intToListVisibility(row.getInteger("list_visibility"))!!,
				sourceTags = row.getArrayOfStrings("list_source_tags"),
				sourceExcludeTags = row.getArrayOfStrings("list_source_exclude_tags"),
				sourceMime = row.getString("list_source_mime"),
				sourceCreatedBefore = row.getOffsetDateTime("list_source_created_before"),
				sourceCreatedAfter = row.getOffsetDateTime("list_source_created_after"),
				showAllUserFiles = row.getBoolean("list_show_all_user_files"),
				itemCount = row.getInteger("list_item_count"),
				containsFile = if(row.hasCol("list_contains_file")) row.getBoolean("list_contains_file") else null,
				createdTs = row.getOffsetDateTime("list_created_ts"),
				modifiedTs = row.getOffsetDateTime("list_modified_ts")
			)
		}
	}
}