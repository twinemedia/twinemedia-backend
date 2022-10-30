package net.termer.twinemedia.dataobject

import io.vertx.sqlclient.Row
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.util.intToListType
import net.termer.twinemedia.util.intToListVisibility
import java.time.OffsetDateTime

/**
 * Data class for a list
 * @since 2.0.0
 */
class ListRow(
	override val internalId: Int,
	override val id: String,

	/**
	 * The list's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The list's description
	 * @since 2.0.0
	 */
	val description: String,

	/**
	 * The list creator's account internal ID, or null if the account no longer exists
	 * @since 2.0.0
	 */
	val creatorInternalId: Int?,

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
	 * Whether files by all accounts should be shown in list, not just by the list creator.
	 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED]
	 * @since 2.0.0
	 */
	val showAllAccountFiles: Boolean,

	/**
	 * The number of items in the list.
	 * Only applies to lists with type [ListType.STANDARD], otherwise it will be null.
	 * @since 2.0.0
	 */
	val itemCount: Int?,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): StandardRow {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = ListRow(
			internalId = row.getInteger("id"),
			id = row.getString("list_id"),
			name = row.getString("list_name"),
			description = row.getString("list_description"),
			creatorInternalId = row.getInteger("list_creator"),
			// Allow throwing of NPE here because an invalid type should never have been in the database in the first place
			type = intToListType(row.getInteger("list_type"))!!,
			// Same rationale for this column as well
			visibility = intToListVisibility(row.getInteger("list_visibility"))!!,
			sourceTags = row.getArrayOfStrings("list_source_tag_ids"),
			sourceExcludeTags = row.getArrayOfStrings("list_source_exclude_tag_ids"),
			sourceMime = row.getString("list_source_mime"),
			sourceCreatedBefore = row.getOffsetDateTime("list_source_created_before"),
			sourceCreatedAfter = row.getOffsetDateTime("list_source_created_after"),
			showAllAccountFiles = row.getBoolean("list_show_all_account_files"),
			itemCount = row.getInteger("list_item_count"),
			createdTs = row.getOffsetDateTime("list_created_ts"),
			modifiedTs = row.getOffsetDateTime("list_modified_ts")
		)
	}
}