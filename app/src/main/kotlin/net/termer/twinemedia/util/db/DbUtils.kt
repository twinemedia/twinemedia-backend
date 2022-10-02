package net.termer.twinemedia.util

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import net.termer.twinemedia.db.Database
import org.jooq.Query
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
suspend fun Query.fetchManyAsync() = Database.client
	.query(getSQL(ParamType.INLINED))
	.execute().await()

/**
 * Fetches query results and returns the first row
 * @return The first result row
 * @since 2.0.0
 */
suspend fun Query.fetchOneAsync() = fetchManyAsync().first()

/**
 * Executes a query
 * @since 2.0.0
 */
suspend fun Query.executeAsync() {
	fetchManyAsync()
}