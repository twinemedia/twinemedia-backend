package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

/**
 * Fetches all tags that match the specified term (term allows all SQL patterns allowed in LIKE comparisons, e.g. %)
 * @param term The term to search for
 * @param offset The offset of the rows to return
 * @param limit The amount of rows to return
 * @return All tags matching the specified term
 * @since 1.0
 */
suspend fun fetchTagsByTerm(term : String, offset : Int, limit : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT * FROM tags
                WHERE tag_name LIKE ?
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
 * @return All tags
 * @since 1.0
 */
suspend fun fetchAllTags(offset : Int, limit : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT * FROM tags
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(offset)
                    .add(limit)
    )
}