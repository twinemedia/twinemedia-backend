package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

/**
 * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
 * Order key:
 * 0 = Alphabetically, ascending
 * 1 = Alphabetically, descending
 * @param order The order
 * @return The appropriate "ORDER BY" SQL for the selected order
 * @since 1.0
 */
private fun orderBy(order : Int) : String {
    return "ORDER BY " + when(order) {
        1 -> "tag_name DESC"
        else -> "tag_name ASC"
    }
}

/**
 * Fetches all tags that match the specified term (term allows all SQL patterns allowed in LIKE comparisons, e.g. %)
 * @param term The term to search for
 * @param offset The offset of the rows to return
 * @param limit The amount of rows to return
 * @param order The order to return the tags
 * @return All tags matching the specified term
 * @since 1.0
 */
suspend fun fetchTagsByTerm(term : String, offset : Int, limit : Int, order : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                tag_name AS name
                FROM tags
                WHERE tag_name LIKE ?
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(term)
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches all tags
 * @param offset The offset of the rows to return
 * @param limit The amount of rows to return
 * @param order The order to return the tags
 * @return All tags
 * @since 1.0
 */
suspend fun fetchAllTags(offset : Int, limit : Int, order : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                tag_name AS name
                FROM tags
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches info about a tag
 * @param tag The tag to get info about
 * @return All info about the specified tag
 * @since 1.0
 */
suspend fun fetchTagInfo(tag : String) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                COUNT(*) AS uses
                FROM media
                WHERE media_tags::jsonb ?? ?
            """.trimIndent(),
            JsonArray().add(tag)
    )
}