package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.util.intToListType
import net.termer.twinemedia.util.intToListVisibility
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for a list
 * @param internalId The list's internal ID, the one not exposed through the API
 * @param id The list's alphanumeric ID
 * @param name The list's name
 * @param description The list's description (can be null)
 * @param creator The list creator's ID
 * @param type The list's type
 * @param visibility The list's visibility
 * @param createdOn The time this list was created
 * @param modifiedOn The time this list was last modified
 * @param sourceTags The tags media must have to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceExcludeTags The tags media must not have to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceMime The MIME type media must have to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceCreatedBefore The time media must have been uploaded to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceCreatedAfter The time media must have been uploaded to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param showAllUserFiles Whether media by all users should be shown in list, not just by the list creator (specify false if type is not AUTOMATICALLY_POPULATED)
 * @since 1.4.0
 */
@DataObject
class List(
		/**
		 * The list's internal ID, the one not exposed through the API
		 * @since 1.4.0
		 */
		val internalId: Int,
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
		 * The list creator's ID
		 * @since 1.4.0
		 */
		val creator: Int,
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
		 * The time this list was created
		 * @since 1.4.0
		 */
		val createdOn: OffsetDateTime,
		/**
		 * The time this list was last modified
		 * @since 1.4.0
		 */
		val modifiedOn: OffsetDateTime,
		/**
		 * The tags media must have to be in this list.
		 * Only applies to lists with type AUTOMATICALLY_POPULATED, will be null for other types.
		 * @since 1.4.0
		 */
		val sourceTags: Array<String>?,
		/**
		 * The tags media must not have to be in this list.
		 * Only applies to lists with type AUTOMATICALLY_POPULATED, will be null for other types.
		 * @since 1.4.0
		 */
		val sourceExcludeTags: Array<String>?,
		/**
		 * The MIME type media must have to be in this list.
		 * Only applies to lists with type AUTOMATICALLY_POPULATED, will be null for other types.
		 * @since 1.4.0
		 */
		val sourceMime: String?,
		/**
		 * The time media must have been uploaded before to be in this list.
		 * Only applies to lists with type AUTOMATICALLY_POPULATED, will be null for other types.
		 * @since 1.4.0
		 */
		val sourceCreatedBefore: OffsetDateTime?,
		/**
		 * The time media must have been uploaded after to be in this list.
		 * Only applies to lists with type AUTOMATICALLY_POPULATED, will be null for other types.
		 * @since 1.4.0
		 */
		val sourceCreatedAfter: OffsetDateTime?,
		/**
		 * Whether media by all users should be shown in list, not just by the list creator.
		 * Only applies to lists with type AUTOMATICALLY_POPULATED.
		 * @since 1.4.2
		 */
		val showAllUserFiles: Boolean
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<List> { row ->
			List(
					internalId = row.getInteger("id"),
					id = row.getString("list_id"),
					name = row.getString("list_name"),
					description = row.getString("list_description"),
					creator = row.getInteger("list_creator"),
					// Allow throwing of NPE here because an invalid type should never have been in the database in the first place
					type = intToListType(row.getInteger("list_type"))!!,
					// Same rationale for this column as well
					visibility = intToListVisibility(row.getInteger("list_visibility"))!!,
					createdOn = row.getOffsetDateTime("list_created_on"),
					modifiedOn = row.getOffsetDateTime("list_modified_on"),
					sourceTags = row.getJsonArray("list_source_tags")?.toStringArray(),
					sourceExcludeTags = row.getJsonArray("list_source_exclude_tags")?.toStringArray(),
					sourceMime = row.getString("list_source_mime"),
					sourceCreatedBefore = row.getOffsetDateTime("list_source_created_before"),
					sourceCreatedAfter = row.getOffsetDateTime("list_source_created_after"),
					showAllUserFiles = row.getBoolean("list_show_all_user_files")
			)
		}
	}
}