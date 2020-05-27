package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

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
private fun orderBy(order : Int) : String {
    return "ORDER BY " + when(order) {
        1 -> "list_created_on ASC"
        2 -> "list_name ASC"
        3 -> "list_name DESC"
        else -> "list_created_on DESC"
    }
}

/**
 * Creates a new list
 * @param id The alphanumeric list ID
 * @param name The name of the list
 * @param description The description for the list
 * @param creator The ID of the account that created the list
 * @param type The list's type
 * @param sourceTags The tags media must possess in order to be displayed in this list (can be null, only applies if type is 1)
 * @param sourceCreatedBefore The ISO date String media must be created before in order to be displayed in this list (can be null, only applies if type is 1)
 * @param sourceCreatedAfter The ISO date String media must be created after in order to be displayed in this list (can be null, only applies if type is 1)
 * @param sourceMime The media MIME pattern that media must match in order to be displayed in this list (can be null, only applies if type is 1, allows % for use as wildcards)
 * @since 1.0
 */
suspend fun createList(id : String, name : String, description : String, creator : Int, type : Int, sourceTags : JsonArray?, sourceCreatedBefore : String?, sourceCreatedAfter : String?, sourceMime : String?) {
    client?.queryWithParamsAwait(
            """
                INSERT INTO lists
                ( list_id, list_name, list_description, list_creator, list_type, list_source_tags, list_source_created_before, list_source_created_after, list_source_mime, list_created_on )
                VALUES
                ( ?, ?, ?, ?, ?, CAST( ? AS jsonb ), ?, ?, ?, NOW() )
            """.trimIndent(),
            JsonArray()
                    .add(id)
                    .add(name)
                    .add(description)
                    .add(creator)
                    .add(type)
                    .add(sourceTags.toString())
                    .add(sourceCreatedBefore)
                    .add(sourceCreatedAfter)
                    .add(sourceMime)
    )
}

/**
 * Fetches the list with the specified ID
 * @return All rows from database search
 * @since 1.0
 */
suspend fun fetchList(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            "SELECT * FROM lists WHERE id = ?",
            JsonArray().add(id)
    )
}

/**
 * Fetches the list with the specified generated ID
 * @return All rows from database search
 * @since 1.0
 */
suspend fun fetchList(listId : String) : ResultSet? {
    return client?.queryWithParamsAwait(
            "SELECT * FROM lists WHERE list_id = ?",
            JsonArray().add(listId)
    )
}

/**
 * Fetches info about a list. Note: the returned "internal_id" field should be removed before serving info the the client.
 * @param listId The alphanumeric generated list ID to search
 * @return The info for the specified list
 * @since 1.0
*/
suspend fun fetchListInfo(listId : String) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                	CASE WHEN list_type=1 THEN (
                		-1
                	) ELSE (
                		SELECT COUNT(*) FROM listitems
                		WHERE item_list = lists.id
                	) END AS item_count,
                	lists.id AS internal_id,
                	list_id AS id,
                	list_name AS name,
                	list_description AS description,
                	list_type AS type,
                	list_created_on AS created_on,
                	list_source_tags AS source_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                WHERE list_id = ?
            """.trimIndent(),
            JsonArray().add(listId)
    )
}

/**
 * Fetches info about a list. Note: the returned "internal_id" field should be removed before serving info the the client.
 * @param id The list's internal ID
 * @return The info for the specified list
 * @since 1.0
 */
suspend fun fetchListInfo(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                	CASE WHEN list_type=1 THEN (
                		-1
                	) ELSE (
                		SELECT COUNT(*) FROM listitems
                		WHERE item_list = lists.id
                	) END AS item_count,
                	lists.id AS internal_id,
                	list_id AS id,
                	list_name AS name,
                	list_description AS description,
                	list_type AS type,
                	list_created_on AS created_on,
                	list_source_tags AS source_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                WHERE id = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}

/**
 * Fetches a list of lists
 * @param offset The offset of the lists to fetch
 * @param limit The amount of lists to return
 * @param order The order to return the lists
 * @return All lists in the specified range
 * @since 1.0
 */
suspend fun fetchLists(offset : Int, limit : Int, order : Int): ResultSet? {
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
                	list_created_on AS created_on,
                	list_source_tags AS source_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
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
 * @param searchNames Whether to search the names of lists
 * @param searchDescs Whether to search the descriptions of lists
 * @return All lists matching the specified plaintext query
 * @since 1.0
 */
suspend fun fetchListsByPlaintextQuery(query : String, offset : Int, limit : Int, order : Int, searchNames : Boolean, searchDescs : Boolean): ResultSet? {
    val params = JsonArray()
    var tsvectorParts = arrayListOf<String>()
    if(searchNames)
        tsvectorParts.add("COALESCE(list_name, '')")
    if(searchDescs)
        tsvectorParts.add("COALESCE(list_description, '')")

    val tsvectorStr = if(tsvectorParts.size > 0)
            """
                WHERE
                to_tsvector(
                    ${ tsvectorParts.joinToString(" || ' ' || ") }
                ) @@ plainto_tsquery(?)
            """.trimIndent().also {
            params.add(query)
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
                	list_id AS id,
                	list_name AS name,
                	list_description AS description,
                	list_type AS type,
                	list_created_on AS created_on,
                	list_source_tags AS source_tags,
                	list_source_created_before AS source_created_before,
                	list_source_created_after AS source_created_after,
                    list_source_mime AS source_mime,
                    list_creator AS creator,
                	account_name AS creator_name
                FROM lists
                LEFT JOIN accounts ON accounts.id = list_creator
                $tsvectorStr
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            params
                    .add(offset)
                    .add(limit)
    )
}