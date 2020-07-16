package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.util.UserAccount

/**
 * Database model for tags
 * @since 1.2.0
 */
class TagsModel {
    private var _account: UserAccount? = null

    /**
     * The account associated with this model instance
     * @since 1.2.0
     */
    var account: UserAccount?
        get() = _account
        set(value) { _account = value }

    /**
     * Creates a new TagsModel with an account
     * @param account The account to use for this model instance
     * @since 1.2.0
     */
    constructor(account: UserAccount) {
        this.account = account
    }
    /**
     * Creates a new TagsModel without an account
     * @since 1.2.0
     */
    constructor() {
        this.account = null
    }

    /**
     * Applies a default SQL WHERE filter for tags based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun whereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherTags -> {
                "tag_creator = ${account?.id}"
            }
            account!!.hasPermission("tags.list.all") -> {
                "TRUE"
            }
            else -> {
                "tag_creator = ${account?.id}"
            }
        }
    }

    /**
     * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
     * Order key:
     * 0 = Alphabetically, ascending
     * 1 = Alphabetically, descending
     * 2 = Tag length, ascending
     * 3 = Tag length, descending
     * 4 = Tag uses, ascending
     * 5 = Tag uses, descending
     * @param order The order
     * @return The appropriate "ORDER BY" SQL for the selected order
     * @since 1.0
     */
    private fun orderBy(order: Int): String {
        return "ORDER BY " + when (order) {
            1 -> "tag_name DESC"
            2 -> "CHAR_LENGTH(tag_name) ASC"
            3 -> "CHAR_LENGTH(tag_name) DESC"
            4 -> "files ASC"
            5 -> "files DESC"
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
    suspend fun fetchTagsByTerm(term: String, offset: Int, limit: Int, order: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    tag_name AS name,
                    (
                        SELECT
                            SUM(tag_files)
                        FROM tags
                        WHERE
                        ${whereFilter()}
                        AND tag_name = distinct_tags.tag_name
                    ) AS files
                FROM
                (
                    SELECT DISTINCT
                        tag_name
                    FROM tags
                    WHERE
                    ${whereFilter()}
                    AND tag_name LIKE ?
                ) AS distinct_tags
                ${orderBy(order)}
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
    suspend fun fetchAllTags(offset: Int, limit: Int, order: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    tag_name AS name,
                    (
                        SELECT
                            SUM(tag_files)
                        FROM tags
                        WHERE
                        ${whereFilter()}
                        AND tag_name = distinct_tags.tag_name
                    ) AS files
                FROM
                (
                    SELECT DISTINCT
                        tag_name
                    FROM tags
                    WHERE
                    ${whereFilter()}
                ) AS distinct_tags
                ${orderBy(order)}
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
    suspend fun fetchTagInfo(tag: String): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                COUNT(*) AS uses
                FROM media
                WHERE
                ${whereFilter()}
                AND media_tags::jsonb ?? ?
            """.trimIndent(),
                JsonArray().add(tag)
        )
    }

    /**
     * Refreshes the tags materialized view
     * @since 1.0
     */
    suspend fun refreshTags(): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                REFRESH MATERIALIZED VIEW tags
            """.trimIndent(),
                JsonArray()
        )
    }
}