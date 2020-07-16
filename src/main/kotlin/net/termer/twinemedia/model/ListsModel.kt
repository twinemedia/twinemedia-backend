package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.util.UserAccount

/**
 * Database model for lists
 * @since 1.2.0
 */
class ListsModel {
    private var _account: UserAccount? = null

    /**
     * The account associated with this model instance
     * @since 1.2.0
     */
    var account: UserAccount?
        get() = _account
        set(value) { _account = value }

    /**
     * Creates a new ListsModel with an account
     * @param account The account to use for this model instance
     * @since 1.2.0
     */
    constructor(account: UserAccount) {
        this.account = account
    }
    /**
     * Creates a new ListsModel without an account
     * @since 1.2.0
     */
    constructor() {
        this.account = null
    }

    /**
     * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
     * Order key:
     * 0 = Creation date, newest to oldest
     * 1 = Creation date, oldest to newest
     * 2 = Name, alphabetically, ascending
     * 3 = Name, alphabetically, descending
     * @param order The order
     * @return The appropriate "ORDER BY" SQL for the selected order
     * @since 1.0
     */
    private fun orderBy(order: Int): String {
        return "ORDER BY " + when (order) {
            1 -> "list_created_on ASC"
            2 -> "list_name ASC"
            3 -> "list_name DESC"
            else -> "list_created_on DESC"
        }
    }

    /**
     * Applies a default SQL WHERE filter for listing lists based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun listWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherLists -> {
                "list_creator = ${account?.id}"
            }
            account!!.hasPermission("lists.list.all") -> {
                "TRUE"
            }
            else -> {
                "list_creator = ${account?.id}"
            }
        }
    }
    /**
     * Applies a default SQL WHERE filter for fetching single lists based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun viewWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherLists -> {
                "list_creator = ${account?.id}"
            }
            account!!.hasPermission("lists.view.all") -> {
                "TRUE"
            }
            else -> {
                "list_creator = ${account?.id}"
            }
        }
    }
    /**
     * Applies a default SQL WHERE filter for fetching single lists for editing based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun editWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherLists -> {
                "list_creator = ${account?.id}"
            }
            account!!.hasPermission("lists.edit.all") -> {
                "TRUE"
            }
            else -> {
                "list_creator = ${account?.id}"
            }
        }
    }
    /**
     * Applies a default SQL WHERE filter for fetching single lists for deleting based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun deleteWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherLists -> {
                "list_creator = ${account?.id}"
            }
            account!!.hasPermission("lists.delete.all") -> {
                "TRUE"
            }
            else -> {
                "list_creator = ${account?.id}"
            }
        }
    }

    /**
     * Creates a new list
     * @param id The alphanumeric list ID
     * @param name The name of the list
     * @param description The description for the list
     * @param creator The ID of the account that created the list
     * @param visibility The list's visibility (0 = private, 1 = public)
     * @param type The list's type
     * @param sourceTags The tags media must possess in order to be displayed in this list (can be null, only applies if type is 1)
     * @param sourceExcludeTags The tags media must NOT possess in order to be displayed in this list (can be null, only applies if type is 1)
     * @param sourceCreatedBefore The ISO date String media must be created before in order to be displayed in this list (can be null, only applies if type is 1)
     * @param sourceCreatedAfter The ISO date String media must be created after in order to be displayed in this list (can be null, only applies if type is 1)
     * @param sourceMime The media MIME pattern that media must match in order to be displayed in this list (can be null, only applies if type is 1, allows % for use as wildcards)
     * @since 1.0
     */
    suspend fun createList(id: String, name: String, description: String?, creator: Int, visibility: Int, type: Int, sourceTags: JsonArray?, sourceExcludeTags: JsonArray?, sourceCreatedBefore: String?, sourceCreatedAfter: String?, sourceMime: String?) {
        client?.queryWithParamsAwait(
                """
                INSERT INTO lists
                ( list_id, list_name, list_description, list_creator, list_visibility, list_type, list_source_tags, list_source_exclude_tags, list_source_created_before, list_source_created_after, list_source_mime, list_created_on )
                VALUES
                ( ?, ?, ?, ?, ?, ?, CAST( ? AS jsonb ), CAST( ? AS jsonb ), ?, ?, ?, NOW() )
            """.trimIndent(),
                JsonArray()
                        .add(id)
                        .add(name)
                        .add(description)
                        .add(creator)
                        .add(visibility)
                        .add(type)
                        .add(sourceTags?.toString())
                        .add(sourceExcludeTags?.toString())
                        .add(sourceCreatedBefore)
                        .add(sourceCreatedAfter)
                        .add(sourceMime)
        )
    }

    /**
     * Creates a new list item entry
     * @param list The list to add the item to
     * @param item The item to add to the list
     * @since 1.0
     */
    suspend fun createListItem(list: Int, item: Int) {
        client?.queryWithParamsAwait(
                """
                INSERT INTO listitems
                ( item_list, item_media )
                VALUES
                ( ?, ? )
            """.trimIndent(),
                JsonArray()
                        .add(list)
                        .add(item)
        )
    }

    /**
     * Fetches the list with the specified ID
     * @return All rows from database search
     * @since 1.0
     */
    suspend fun fetchList(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                "SELECT * FROM lists WHERE ${viewWhereFilter()} AND id = ?",
                JsonArray().add(id)
        )
    }

    /**
     * Fetches the list with the specified generated ID
     * @return All rows from database search
     * @since 1.0
     */
    suspend fun fetchList(listId: String): ResultSet? {
        return client?.queryWithParamsAwait(
                "SELECT * FROM lists WHERE ${viewWhereFilter()} AND list_id = ?",
                JsonArray().add(listId)
        )
    }

    /**
     * Fetches info about a list
     * @param listId The alphanumeric generated list ID to search
     * @return The info for the specified list
     * @since 1.0
     */
    suspend fun fetchListInfo(listId: String): ResultSet? {
        return client?.queryWithParamsAwait(
                """
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
                	list_source_tags AS source_tags,
                    list_source_exclude_tags AS source_exclude_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                WHERE
                ${viewWhereFilter()}
                AND list_id = ?
            """.trimIndent(),
                JsonArray().add(listId)
        )
    }

    /**
     * Fetches info about a list
     * @param id The list's internal ID
     * @return The info for the specified list
     * @since 1.0
     */
    suspend fun fetchListInfo(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
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
                	list_source_tags AS source_tags,
                    list_source_exclude_tags AS source_exclude_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                WHERE
                ${viewWhereFilter()}
                AND id = ?
            """.trimIndent(),
                JsonArray().add(id)
        )
    }

    /**
     * Fetches a list of lists
     * @param offset The offset of the lists to fetch
     * @param limit The amount of lists to return
     * @param order The order to return the lists
     * @param type The type of lists to return (specify -1 for any)
     * @param mediaContainCheck The media to check if lists contain (can be null to omit)
     * @return All lists in the specified range
     * @since 1.0
     */
    suspend fun fetchLists(offset: Int, limit: Int, order: Int, type: Int, mediaContainCheck: String?): ResultSet? {
        val params = JsonArray()
        val containsStr = if (mediaContainCheck == null)
            ""
        else
            """
            (
                SELECT COUNT(*) FROM listitems
                LEFT JOIN media ON media.id = item_media
                WHERE media_id = ?
                AND item_list = lists.id
            ) = 1 AS contains_media,
        """.trimIndent().also {
                params.add(mediaContainCheck)
            }
        val listStr = if (type > -1)
            "AND list_type = ?".also {
                params.add(type)
            }
        else
            ""

        return client?.queryWithParamsAwait(
                """
                SELECT
                    CASE WHEN list_type = 1 THEN (
                        -1
                    ) ELSE (
                        SELECT COUNT(*) FROM listitems
                        WHERE item_list = lists.id
                    ) END AS item_count,
                    $containsStr
                	list_id AS id,
                	list_name AS name,
                	list_description AS description,
                	list_type AS type,
                    list_visibility AS visibility,
                	list_created_on AS created_on,
                	list_source_tags AS source_tags,
                    list_source_exclude_tags AS source_exclude_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                WHERE
                ${listWhereFilter()}
                $listStr
                ${orderBy(order)}
                OFFSET ? LIMIT ?
            """.trimIndent(),
                params
                        .add(offset)
                        .add(limit)
        )
    }

    /**
     * Fetches a list of lists
     * @param query The plaintext query to search for
     * @param offset The offset of the lists to fetch
     * @param limit The amount of lists to return
     * @param order The order to return the lists
     * @param type The type of lists to return (specify -1 for any)
     * @param mediaContainCheck The media to check if lists contain (can be null to omit)
     * @param searchNames Whether to search the names of lists
     * @param searchDescs Whether to search the descriptions of lists
     * @return All lists matching the specified plaintext query
     * @since 1.0
     */
    suspend fun fetchListsByPlaintextQuery(query: String, offset: Int, limit: Int, order: Int, type: Int, mediaContainCheck: String?, searchNames: Boolean, searchDescs: Boolean): ResultSet? {
        val params = JsonArray()

        val containsStr = if (mediaContainCheck == null)
            ""
        else
            """
            (
                SELECT COUNT(*) FROM listitems
                LEFT JOIN media ON media.id = item_media
                WHERE media_id = ?
                AND item_list = lists.id
            ) = 1 AS contains_media,
        """.trimIndent().also {
                params.add(mediaContainCheck)
            }

        val searchParts = ArrayList<String>()
        if (searchNames)
            searchParts.add("COALESCE(list_name, '')")
        if (searchDescs)
            searchParts.add("COALESCE(list_description, '')")

        val searchStr = if (searchParts.size > 0)
            """
                AND (to_tsvector(
                    ${searchParts.joinToString(" || ' ' || ")}
                ) @@ plainto_tsquery(?) OR
                LOWER(${searchParts.joinToString(" || ' ' || ")}) LIKE LOWER(?))
            """.trimIndent().also {
                params.add(query)
                params.add("%$query%")
            }
        else
            ""
        val typeStr = if (type > -1)
            """
            AND list_type = ?
        """.trimIndent().also {
                params.add(type)
            }
        else
            ""

        return client?.queryWithParamsAwait(
                """
                SELECT
                    CASE WHEN list_type=1 THEN (
                        -1
                    ) ELSE (
                        SELECT COUNT(*) FROM listitems
                        WHERE item_list = lists.id
                    ) END AS item_count,
                    $containsStr
                	list_id AS id,
                	list_name AS name,
                	list_description AS description,
                	list_type AS type,
                	list_created_on AS created_on,
                	list_source_tags AS source_tags,
                    list_source_exclude_tags AS source_exclude_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                WHERE
                ${listWhereFilter()}
                $searchStr
                $typeStr
                ${orderBy(order)}
                OFFSET ? LIMIT ?
            """.trimIndent(),
                params
                        .add(offset)
                        .add(limit)
        )
    }

    /**
     * Returns whether the provided file ID is in the specified list
     * @param list The list's ID
     * @param item The item's ID
     * @return A row named "contains_media" showing whether the item is contained in the list
     * @since 1.0
     */
    suspend fun fetchListContainsItem(list: Int, item: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                	(COUNT(*) = 1) AS contains_media
                FROM listitems
                WHERE item_media = ?
                AND item_list = ?
            """.trimIndent(),
                JsonArray()
                        .add(item)
                        .add(list)
        )
    }

    /**
     * Updates a list to be a normal list with the provided info
     * @param id The list's ID
     * @param name The list's name
     * @param description The list's description (can be null)
     * @since 1.0
     */
    suspend fun updateListToNormal(id: Int, name: String, description: String?, visibility: Int) {
        client?.queryWithParamsAwait(
                """
                UPDATE lists
                SET
                	list_name = ?,
                	list_description = ?,
                	list_visibility = ?,
                    list_type = 0,
                    list_source_tags = NULL,
                    list_source_exclude_tags = NULL,
                    list_source_created_before = NULL,
                    list_source_created_after = NULL,
                    list_source_mime = NULL
                WHERE
                ${editWhereFilter()}
                AND id = ?
            """.trimIndent(),
                JsonArray()
                        .add(name)
                        .add(description)
                        .add(visibility)
                        .add(id)
        )
    }

    /**
     * Updates a list to be an automatically populated list with the provided info
     * @param id The list's ID
     * @param name The list's name
     * @param description The list's description (can be null)
     * @param sourceTags The tags media must possess in order to be displayed in this list (can be null)
     * @param sourceExcludeTags The tags media must NOT possess in order to be displayed in this list (can be null)
     * @param sourceCreatedBefore The ISO date String media must be created before in order to be displayed in this list (can be null)
     * @param sourceCreatedAfter The ISO date String media must be created after in order to be displayed in this list (can be null)
     * @param sourceMime The media MIME pattern that media must match in order to be displayed in this list (can be null, allows % for use as wildcards)
     * @since 1.0
     */
    suspend fun updateListToAutomaticallyPopulated(id: Int, name: String, description: String?, visibility: Int, sourceTags: JsonArray?, sourceExcludeTags: JsonArray?, sourceCreatedBefore: String?, sourceCreatedAfter: String?, sourceMime: String?) {
        client?.queryWithParamsAwait(
                """
                UPDATE lists
                SET
                	list_name = ?,
                	list_description = ?,
                	list_visibility = ?,
                    list_type = 1,
                    list_source_tags = CAST( ? AS jsonb ),
                    list_source_exclude_tags = CAST( ? AS jsonb ),
                    list_source_created_before = ?,
                    list_source_created_after = ?,
                    list_source_mime = ?
                WHERE
                ${editWhereFilter()}
                AND id = ?
            """.trimIndent(),
                JsonArray()
                        .add(name)
                        .add(description)
                        .add(visibility)
                        .add(sourceTags?.toString())
                        .add(sourceExcludeTags?.toString())
                        .add(sourceCreatedBefore)
                        .add(sourceCreatedAfter)
                        .add(sourceMime)
                        .add(id)
        )
    }

    /**
     * Deletes the list with the specified alphanumeric list ID
     * @param listId The alphanumeric list ID
     * @since 1.0
     */
    suspend fun deleteList(listId: String) {
        client?.queryWithParamsAwait(
                """
                DELETE FROM lists WHERE ${deleteWhereFilter()} AND list_id = ?
            """.trimIndent(),
                JsonArray().add(listId)
        )
    }

    /**
     * Deletes all list items that belong to the specified list
     * @param listId The list ID
     * @since 1.0
     */
    suspend fun deleteListItemsByListId(listId: Int) {
        client?.queryWithParamsAwait(
                """
                DELETE FROM listitems WHERE item_list = ?
            """.trimIndent(),
                JsonArray().add(listId)
        )
    }

    /**
     * Deletes all list items that are for the specified media file ID
     * @param mediaId The media file ID
     * @since 1.0
     */
    suspend fun deleteListItemsByMediaId(mediaId: Int) {
        client?.queryWithParamsAwait(
                """
                DELETE FROM listitems WHERE item_media = ?
            """.trimIndent(),
                JsonArray().add(mediaId)
        )
    }

    /**
     * Deletes a list item based on the item and list ID
     * @param list The list's ID
     * @param file The file's ID
     * @since 1.0
     */
    suspend fun deleteListItemByListAndFile(list: Int, file: Int) {
        client?.queryWithParamsAwait(
                """
                DELETE FROM listitems
                WHERE item_media = ?
                AND item_list = ?
            """.trimIndent(),
                JsonArray()
                        .add(file)
                        .add(list)
        )
    }
}