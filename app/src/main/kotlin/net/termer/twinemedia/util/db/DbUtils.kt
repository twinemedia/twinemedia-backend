package net.termer.twinemedia.util.db

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import net.termer.twinemedia.dataobject.StandardRow
import net.termer.twinemedia.model.pagination.CommonRowMapper
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.ROW_ID_CHARS
import net.termer.twinemedia.util.genSecureStrOf
import org.jooq.Query
import org.jooq.SelectQuery
import org.jooq.conf.ParamType

/**
 * Returns whether the specified column is present in this row
 * @param col The name of the column to check for
 * @return Whether the specified column is present in this row
 * @since 2.0.0
 */
fun Row.hasCol(col: String) = getColumnIndex(col) != -1

/**
 * Generates a new row ID
 * @param len The length of the ID (defaults to 10)
 * @return The newly generated row ID
 * @since 2.0.0
 */
fun genRowId(len: Int = 10) = genSecureStrOf(ROW_ID_CHARS, len)

/**
 * Fetches query results and returns all rows
 * @return All result rows
 * @since 2.0.0
 */
suspend fun Query.fetchManyAsync() =
	Database.client
		.query(getSQL(ParamType.INLINED))
		.execute().await()

/**
 * Fetches query results and returns the first row
 * @return The first result row
 * @since 2.0.0
 */
suspend fun Query.fetchOneAsync() = fetchManyAsync().first()

/**
 * Fetches results from a query using the provided [RowPagination] object.
 * Queries that this is called on should not be reused, as they are modified by the pagination [RowPagination.fetch] call used in this function.
 * @param pagination The pagination to use
 * @param mapper The row to object mapper
 * @param limit The limit of results to return
 * @return The paginated query results
 * @since 2.0.0
 */
suspend fun <TRow: StandardRow, TSortEnum: Enum<TSortEnum>, TColType> SelectQuery<*>.fetchPaginatedAsync(
	pagination: RowPagination<TRow, TSortEnum, TColType>,
	mapper: CommonRowMapper<TRow>,
	limit: Int
) = pagination.fetch(this, mapper, limit)

/**
 * Executes a query
 * @since 2.0.0
 */
suspend fun Query.executeAsync() {
	Database.client
		.query(getSQL(ParamType.INLINED))
		.execute().await()
}