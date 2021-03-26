package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.db.containsColumn
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.util.intToListType
import net.termer.twinemedia.util.intToListVisibility
import net.termer.twinemedia.util.toJsonArray
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for a list's info
 * @param id The list's alphanumeric ID
 * @param name The list's name
 * @param description The list's description (can be null)
 * @param creator The list creator's ID
 * @param creatorName The list creator's name
 * @param type The list's type
 * @param visibility The list's visibility
 * @param createdOn The time this list was created
 * @param modifiedOn The time this list was last modified
 * @param sourceTags The tags media must have to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceExcludeTags The tags media must not have to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceMime The MIME type media must have to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceCreatedBefore The time media must have been uploaded to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param sourceCreatedAfter The time media must have been uploaded to be in this list (can be null) (specify null if type is not AUTOMATICALLY_POPULATED)
 * @param itemCount The amount of items this list has (specify -1 if type is not STANDARD)
 * @since 1.4.0
 */
@DataObject
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
		 * The list creator's ID
		 * @since 1.4.0
		 */
		val creator: Int,
		/**
		 * The list creator's name (null if the account doesn't exist)
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
		 * The amount of items this list has.
		 * Only applies to lists with type STANDARD, will be -1 for other types.
		 * @since 1.4.0
		 */
		val itemCount: Int,
		/**
		 * Whether this list contains a media file that was specified in a query.
		 * Will be null if none was specified.
		 * @since 1.4.0
		 */
		val containsMedia: Boolean?
) {
	/**
	 * Returns a JSON representation of the list's info
	 * @return A JSON representation of the list's info
	 * @since 1.4.0
	 */
	fun toJson(): JsonObject {
		val json = json {
			obj(
					"id" to id,
					"name" to name,
					"description" to description,
					"creator" to creator,
					"creator_name" to creatorName,
					"type" to type.ordinal,
					"visibility" to visibility.ordinal,
					"created_on" to createdOn.toString(),
					"modified_on" to modifiedOn.toString(),
					"source_tags" to sourceTags?.toJsonArray(),
					"source_exclude_tags" to sourceExcludeTags?.toJsonArray(),
					"source_created_before" to sourceCreatedBefore?.toString(),
					"source_created_after" to sourceCreatedAfter?.toString(),
					"source_mime" to sourceMime,
					"item_count" to itemCount
			)
		}

		if(containsMedia != null)
			json.put("contains_media", containsMedia)

		return json
	}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<ListInfo> { row ->
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
					createdOn = row.getOffsetDateTime("created_on"),
					modifiedOn = row.getOffsetDateTime("modified_on"),
					sourceTags = row.getJsonArray("source_tags")?.toStringArray(),
					sourceExcludeTags = row.getJsonArray("source_exclude_tags")?.toStringArray(),
					sourceMime = row.getString("source_mime"),
					sourceCreatedBefore = row.getOffsetDateTime("source_created_before"),
					sourceCreatedAfter = row.getOffsetDateTime("source_created_after"),
					itemCount = row.getInteger("item_count"),
					containsMedia = if(row.containsColumn("contains_media")) row.getBoolean("contains_media") else null
			)
		}
	}
}