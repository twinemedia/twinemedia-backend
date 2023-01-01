package net.termer.twinemedia.util.db

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import net.termer.twinemedia.dataobject.StandardRow
import net.termer.twinemedia.model.pagination.CommonRowMapper
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.ROW_ID_CHARS
import net.termer.twinemedia.util.genSecureStrOf
import org.jooq.Condition
import org.jooq.Query
import org.jooq.SelectQuery
import org.jooq.conf.ParamType
import org.jooq.impl.DSL.*

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
suspend fun Query.fetchManyAwait() =
	Database.client
		.query(getSQL(ParamType.INLINED))
		.execute().await()

/**
 * Fetches query results and returns the first row.
 * If this is a [SelectQuery] instance, a limit of 1 will be added to it before fetching results.
 * @return The first result row, or null if there was none
 * @since 2.0.0
 */
suspend fun Query.fetchOneAwait(): Row? {
	if(this is SelectQuery<*>)
		addLimit(1)

	val res = fetchManyAwait()

	return if(res.size() > 0)
		res.first()
	else
		null
}

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
	limit: Int,
	mapper: CommonRowMapper<TRow>
) = pagination.fetch(this, limit, mapper)

/**
 * Executes a query
 * @since 2.0.0
 */
suspend fun Query.executeAwait() {
	Database.client
		.preparedQuery(sql)
		.execute(Tuple.wrap(params.entries.toMutableList())).await()
	// TODO Make sure the params are bound properly. If they are not, then we have a problem.
}

/**
 * Generates fulltext search query conditions.
 * Returns no conditions if [searchColumns] is empty.
 * @param searchColumns The columns to search (DO NOT allow user-supplied values here; they are not escaped)
 * @return The resulting [Condition] values
 * @since 2.0.0
 */
fun genFulltextSearchConditions(query: String, searchColumns: MutableList<String>): MutableList<Condition> {
	return if(searchColumns.isNotEmpty()) {
		arrayListOf(condition(
			"(to_tsvector(${searchColumns.joinToString(" || ' ' || ")}) @@ plainto_tsquery({0})" +
					"OR LOWER(${searchColumns.joinToString(" || ' ' || ")}) LIKE LOWER({1}))",
			`val`(query),
			`val`("%$query%")
		))
	} else {
		ArrayList(0)
	}
}
