package net.termer.twinemedia.model

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.dataobject.List
import net.termer.twinemedia.dataobject.ListInfo
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.util.toJsonArray
import java.time.OffsetDateTime

/**
 * Database model for lists
 * @since 1.2.0
 */
@Suppress("UNCHECKED_CAST")
class ListsModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [ListsModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = ListsModel(null, true)
	}

	/**
	 * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
	 * Order key:
	 * 0 = Creation date, newest to oldest
	 * 1 = Creation date, oldest to newest
	 * 2 = Name, alphabetically, ascending
	 * 3 = Name, alphabetically, descending
	 * 4 = Modified date, newest to oldest
	 * 5 = Modified date, oldest to newest
	 * @param order The order
	 * @return The appropriate "ORDER BY" SQL for the selected order
	 */
	private fun orderBy(order: Int): String {
		return "ORDER BY " + when(order) {
			1 -> "list_created_on ASC"
			2 -> "list_name ASC"
			3 -> "list_name DESC"
			4 -> "list_modified_on DESC"
			5 -> "list_modified_on ASC"
			else -> "list_created_on DESC"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for listing lists based on the context associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun listWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherLists -> "list_creator = ${context!!.account.id}"
			context!!.account.hasPermission("lists.list.all") -> "TRUE"
			else -> "list_creator = ${context!!.account.id}"
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single lists based on the context associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun viewWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherLists -> "list_creator = ${context!!.account.id}"
			context!!.account.hasPermission("lists.view.all") -> "TRUE"
			else -> "list_creator = ${context!!.account.id}"
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single lists for editing based on the context associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun editWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherLists -> "list_creator = ${context!!.account.id}"
			context!!.account.hasPermission("lists.edit.all") -> "TRUE"
			else -> "list_creator = ${context!!.account.id}"
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single lists for deleting based on the context associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun deleteWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherLists -> "list_creator = ${context!!.account.id}"
			context!!.account.hasPermission("lists.delete.all") -> "TRUE"
			else -> "list_creator = ${context!!.account.id}"
		}
	}

	/**
	 * SELECT statement for getting info
	 * @param extra Extra rows to select (can be null for none)
	 * @return The SELECT statement
	 */
	private fun infoSelect(extra: String?): String {
		return """
			SELECT
				CASE WHEN list_type=1 THEN (
					-1
				) ELSE (
					SELECT COUNT(*) FROM listitems
					WHERE item_list = lists.id
				) END AS item_count,
				list_id AS id,
				list_name AS name,
				list_description AS description,
				list_type AS type,
				list_visibility AS visibility,
				list_created_on AS created_on,
				list_modified_on AS modified_on,
				list_source_tags AS source_tags,
				list_source_exclude_tags AS source_exclude_tags,
				list_source_created_before AS source_created_before,
				list_source_created_after AS source_created_after,
				list_source_mime AS source_mime,
				list_show_all_user_files AS show_all_user_files,
				list_creator AS creator,
				account_name AS creator_name
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM lists
			LEFT JOIN accounts ON accounts.id = list_creator
		""".trimIndent()
	}

	/**
	 * SELECT statement for getting info
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect() = infoSelect(null)

	/**
	 * Creates a new list
	 * @param id The alphanumeric list ID
	 * @param name The name of the list
	 * @param description The description for the list
	 * @param creator The ID of the account that created the list
	 * @param visibility The list's visibility
	 * @param type The list's type
	 * @param sourceTags The tags media must possess in order to be displayed in this list (can be null, only applies if type is AUTOMATICALLY_POPULATED)
	 * @param sourceExcludeTags The tags media must NOT possess in order to be displayed in this list (can be null, only applies if type is AUTOMATICALLY_POPULATED)
	 * @param sourceCreatedBefore The time media must be created before in order to be displayed in this list (can be null, only applies if type is AUTOMATICALLY_POPULATED)
	 * @param sourceCreatedAfter The time media must be created after in order to be displayed in this list (can be null, only applies if type is AUTOMATICALLY_POPULATED)
	 * @param sourceMime The media MIME pattern that media must match in order to be displayed in this list (can be null, only applies if type is AUTOMATICALLY_POPULATED, allows % for use as wildcards)
	 * @param showAllUserFiles Whether this list will show files from all users and not just the creator's files (only applies if the type is AUTOMATICALLY_POPULATED, otherwise should be false)
	 * @return The newly created list's ID
	 * @since 1.5.0
	 */
	suspend fun createList(id: String, name: String, description: String?, creator: Int, visibility: ListVisibility, type: ListType, sourceTags: Array<String>?, sourceExcludeTags: Array<String>?, sourceCreatedBefore: OffsetDateTime?, sourceCreatedAfter: OffsetDateTime?, sourceMime: String?, showAllUserFiles: Boolean): Int {
		return SqlTemplate
			.forQuery(client, """
				INSERT INTO lists
				( list_id, list_name, list_description, list_creator, list_visibility, list_type, list_source_tags, list_source_exclude_tags, list_source_created_before, list_source_created_after, list_source_mime, list_show_all_user_files )
				VALUES
				( #{id}, #{name}, #{desc}, #{creator}, #{visibility}, #{type}, CAST( #{sourceTags} AS jsonb ), CAST( #{sourceExcludeTags} AS jsonb ), #{sourceCreatedBefore}, #{sourceCreatedAfter}, #{sourceMime}, #{showAllUserFiles} )
				RETURNING id
			""".trimIndent())
			.execute(hashMapOf(
				"id" to id,
				"name" to name,
				"desc" to description,
				"creator" to creator,
				"visibility" to visibility.ordinal,
				"type" to type.ordinal,
				"sourceTags" to sourceTags?.toJsonArray(),
				"sourceExcludeTags" to sourceExcludeTags?.toJsonArray(),
				"sourceCreatedBefore" to sourceCreatedBefore,
				"sourceCreatedAfter" to sourceCreatedAfter,
				"sourceMime" to sourceMime,
				"showAllUserFiles" to (showAllUserFiles)
			)).await()
			.first().getInteger("id")
	}

	/**
	 * Creates a new list item entry
	 * @param list The list to add the item to
	 * @param item The item (media ID) to add to the list
	 * @return The newly created list item's ID
	 * @since 1.5.0
	 */
	suspend fun createListItem(list: Int, item: Int): Int {
		return SqlTemplate
			.forQuery(client, """
				INSERT INTO listitems
				( item_list, item_media )
				VALUES
				( #{list}, #{item} )
				RETURNING id
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"list" to list,
				"item" to item
			)).await()
			.first().getInteger("id")
	}

	/**
	 * Fetches the list with the specified ID
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchList(id: Int): RowSet<List> {
	return SqlTemplate
			.forQuery(client, """
				SELECT * FROM lists WHERE ${viewWhereFilter()} AND id = #{id}
			""".trimIndent())
			.mapTo(List.MAPPER)
			.execute(hashMapOf<String, Any>(
				"id" to id
			)).await()
	}

	/**
	 * Fetches the list with the specified generated ID
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchList(listId: String): RowSet<List> {
		return SqlTemplate
			.forQuery(client, """
				SELECT * FROM lists WHERE ${viewWhereFilter()} AND list_id = #{listId}
			""".trimIndent())
			.mapTo(List.MAPPER)
			.execute(hashMapOf<String, Any>(
				"listId" to listId
			)).await()
	}

	/**
	 * Fetches info about a list
	 * @param listId The alphanumeric generated list ID to search
	 * @return The info for the specified list
	 * @since 1.4.0
	 */
	suspend fun fetchListInfo(listId: String): RowSet<ListInfo> {
		return SqlTemplate
			.forQuery(client, """
				${infoSelect()}
				WHERE
				${viewWhereFilter()}
				AND list_id = #{listId}
			""".trimIndent())
			.mapTo(ListInfo.MAPPER)
			.execute(hashMapOf<String, Any>(
				"listId" to listId
			)).await()
	}

	/**
	 * Fetches info about a list
	 * @param id The list's internal ID
	 * @return The info for the specified list
	 * @since 1.4.0
	 */
	suspend fun fetchListInfo(id: Int): RowSet<ListInfo> {
		return SqlTemplate
			.forQuery(client, """
				${infoSelect()}
				WHERE
				${viewWhereFilter()}
				AND id = #{id}
			""".trimIndent())
			.mapTo(ListInfo.MAPPER)
			.execute(hashMapOf<String, Any>(
				"id" to id
			)).await()
	}

	/**
	 * Fetches a list of list info
	 * @param offset The offset of the lists to fetch
	 * @param limit The amount of lists to return
	 * @param order The order to return the lists
	 * @param type The type of lists to return (specify null for any)
	 * @param mediaContainCheck The media to check if lists contain (can be null to omit)
	 * @return All list info in the specified range
	 * @since 1.4.0
	 */
	suspend fun fetchLists(offset: Int, limit: Int, order: Int, type: ListType?, mediaContainCheck: String?): RowSet<ListInfo> {
		val containsStr = if(mediaContainCheck == null)
			""
		else
			"""
			(
				SELECT COUNT(*) FROM listitems
				LEFT JOIN media ON media.id = item_media
				WHERE media_id = #{media}
				AND item_list = lists.id
			) = 1 AS contains_media
		""".trimIndent()

		val listStr = if(type != null)
			"AND list_type = #{type}"
		else
			""

		return SqlTemplate
			.forQuery(client, """
				${infoSelect(containsStr)}
				WHERE
				${listWhereFilter()}
				$listStr
				${orderBy(order)}
				OFFSET #{offset} LIMIT #{limit}
			""".trimIndent())
			.mapTo(ListInfo.MAPPER)
			.execute(
				hashMapOf(
					"offset" to offset,
					"limit" to limit,
					"type" to type?.ordinal,
					"media" to mediaContainCheck
				) as Map<String, Any?>
			).await()
	}

	/**
	 * Fetches a list of list info
	 * @param query The plaintext query to search for
	 * @param offset The offset of the lists to fetch
	 * @param limit The amount of lists to return
	 * @param order The order to return the lists
	 * @param type The type of lists to return (specify null for any)
	 * @param mediaContainCheck The media to check if lists contain (can be null to omit)
	 * @param searchNames Whether to search the names of lists
	 * @param searchDescs Whether to search the descriptions of lists
	 * @return The info of all lists matching the specified plaintext query
	 * @since 1.4.0
	 */
	suspend fun fetchListsByPlaintextQuery(query: String, offset: Int, limit: Int, order: Int, type: ListType?, mediaContainCheck: String?, searchNames: Boolean, searchDescs: Boolean): RowSet<ListInfo> {
		val containsStr = if(mediaContainCheck == null)
			""
		else
			"""
			(
				SELECT COUNT(*) FROM listitems
				LEFT JOIN media ON media.id = item_media
				WHERE media_id = #{media}
				AND item_list = lists.id
			) = 1 AS contains_media
		"""

		val searchParts = ArrayList<String>()
		if(searchNames)
			searchParts.add("COALESCE(list_name, '')")
		if(searchDescs)
			searchParts.add("COALESCE(list_description, '')")

		val searchStr = if(searchParts.size > 0)
			"""
				AND (to_tsvector(
					${searchParts.joinToString(" || ' ' || ")}
				) @@ plainto_tsquery(#{query) OR
				LOWER(${searchParts.joinToString(" || ' ' || ")}) LIKE LOWER(#{queryLike}))
			"""
		else
			""

		val typeStr = if(type != null)
			"""
			AND list_type = #{type}
		"""
		else
			""

		return SqlTemplate
			.forQuery(client, """
				${infoSelect(containsStr)}
				WHERE
				${listWhereFilter()}
				$searchStr
				$typeStr
				${orderBy(order)}
				OFFSET #{offset} LIMIT #{limit}
			""".trimIndent())
			.mapTo(ListInfo.MAPPER)
			.execute(
				hashMapOf(
					"query" to query,
					"queryLike" to "%$query%",
					"offset" to offset,
					"limit" to limit,
					"type" to type?.ordinal,
					"media" to mediaContainCheck
				) as Map<String, Any?>
			).await()
	}

	/**
	 * Returns whether the provided media file ID is in the specified list
	 * @param list The list's ID
	 * @param item The item (media file)'s ID
	 * @return Whether the item is contained in the list
	 * @since 1.4.0
	 */
	suspend fun fetchListContainsItem(list: Int, item: Int): Boolean {
		return SqlTemplate
			.forQuery(client, """
				SELECT
					(COUNT(*) = 1) AS contains_media
				FROM listitems
				WHERE item_media = #{item}
				AND item_list = #{list}
			""".trimIndent())
			.mapTo { row ->
				row.getBoolean("contains_media")
			}
			.execute(hashMapOf<String, Any>(
				"list" to list,
				"item" to item
			)).await().first()
	}

	/**
	 * Updates a list to be a normal list with the provided info
	 * @param id The list's ID
	 * @param name The list's name
	 * @param description The list's description (can be null)
	 * @param visibility The list's visibility
	 * @since 1.4.0
	 */
	suspend fun updateListToNormal(id: Int, name: String, description: String?, visibility: ListVisibility) {
		SqlTemplate
			.forUpdate(client, """
				UPDATE lists
				SET
					list_name = #{name},
					list_description = #{desc},
					list_visibility = #{visibility},
					list_type = ${ListType.STANDARD.ordinal},
					list_source_tags = NULL,
					list_source_exclude_tags = NULL,
					list_source_created_before = NULL,
					list_source_created_after = NULL,
					list_source_mime = NULL,
					list_show_all_user_files = FALSE,
					list_modified_on = NOW()
				WHERE
				${editWhereFilter()}
				AND id = #{id}
			""".trimIndent())
			.execute(
				hashMapOf(
					"id" to id,
					"name" to name,
					"desc" to description,
					"visibility" to visibility.ordinal
				) as Map<String, Any?>
			).await()
	}

	/**
	 * Updates a list to be an automatically populated list with the provided info
	 * @param id The list's ID
	 * @param name The list's name
	 * @param description The list's description (can be null)
	 * @param visibility The list's visibility
	 * @param sourceTags The tags media must possess in order to be displayed in this list (can be null)
	 * @param sourceExcludeTags The tags media must NOT possess in order to be displayed in this list (can be null)
	 * @param sourceCreatedBefore The time media must be created before in order to be displayed in this list (can be null)
	 * @param sourceCreatedAfter The time media must be created after in order to be displayed in this list (can be null)
	 * @param sourceMime The media MIME pattern that media must match in order to be displayed in this list (can be null, allows % for use as wildcards)
	 * @since 1.4.2
	 */
	suspend fun updateListToAutomaticallyPopulated(id: Int, name: String, description: String?, visibility: ListVisibility, sourceTags: Array<String>?, sourceExcludeTags: Array<String>?, sourceCreatedBefore: OffsetDateTime?, sourceCreatedAfter: OffsetDateTime?, sourceMime: String?, showAllUserFiles: Boolean) {
		SqlTemplate
			.forUpdate(client, """
				UPDATE lists
				SET
					list_name = #{name},
					list_description = #{desc},
					list_visibility = #{visibility},
					list_type = ${ListType.AUTOMATICALLY_POPULATED.ordinal},
					list_source_tags = CAST( #{sourceTags} AS jsonb ),
					list_source_exclude_tags = CAST( #{sourceExcludeTags} AS jsonb ),
					list_source_created_before = #{sourceCreatedBefore},
					list_source_created_after = #{sourceCreatedAfter},
					list_source_mime = #{sourceMime},
					list_show_all_user_files = #{showAllUserFiles},
					list_modified_on = NOW()
				WHERE
				${editWhereFilter()}
				AND id = #{id}
			""".trimIndent())
			.execute(hashMapOf(
				"id" to id,
				"name" to name,
				"desc" to description,
				"visibility" to visibility.ordinal,
				"sourceTags" to sourceTags?.toJsonArray(),
				"sourceExcludeTags" to sourceExcludeTags?.toJsonArray(),
				"sourceCreatedBefore" to sourceCreatedBefore,
				"sourceCreatedAfter" to sourceCreatedAfter,
				"sourceMime" to sourceMime,
				"showAllUserFiles" to showAllUserFiles
			)).await()
	}

	/**
	 * Deletes the list with the specified internal ID
	 * @param id The list ID
	 * @since 1.4.0
	 */
	suspend fun deleteList(id: Int) {
		SqlTemplate
			.forUpdate(client, """
				DELETE FROM lists WHERE ${deleteWhereFilter()} AND id = #{id}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"id" to id
			)).await()
	}

	/**
	 * Deletes the list with the specified alphanumeric list ID
	 * @param listId The alphanumeric list ID
	 * @since 1.0.0
	 */
	suspend fun deleteList(listId: String) {
		SqlTemplate
			.forUpdate(client, """
				DELETE FROM lists WHERE ${deleteWhereFilter()} AND list_id = #{listId}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"listId" to listId
			)).await()
	}

	/**
	 * Deletes all list items that belong to the specified list
	 * @param listId The list ID
	 * @since 1.0.0
	 */
	suspend fun deleteListItemsByListId(listId: Int) {
		SqlTemplate
			.forUpdate(client, """
				DELETE FROM listitems WHERE item_list = #{listId}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"listId" to listId
			)).await()
	}

	/**
	 * Deletes all list items that are for the specified media file ID
	 * @param mediaId The media file ID
	 * @since 1.0.0
	 */
	suspend fun deleteListItemsByMediaId(mediaId: Int) {
		SqlTemplate
			.forUpdate(client, """
				DELETE FROM listitems WHERE item_media = #{mediaId}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"mediaId" to mediaId
			)).await()
	}

	/**
	 * Deletes a list item based on the item (media file) and list ID
	 * @param list The list's ID
	 * @param file The item's ID
	 * @since 1.0.0
	 */
	suspend fun deleteListItemByListAndFile(list: Int, file: Int) {
		SqlTemplate
			.forUpdate(client, """
				DELETE FROM listitems
				WHERE item_media = #{file}
				AND item_list = #{list}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"list" to list,
				"file" to file
			)).await()
	}
}