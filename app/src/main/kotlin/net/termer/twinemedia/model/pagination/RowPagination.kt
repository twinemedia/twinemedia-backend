package net.termer.twinemedia.model.pagination

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.StandardRow
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.SuspendJsonSerializable
import org.jooq.SelectQuery

/**
 * Interface that defines methods and properties related to database row pagination.
 * Implementations should handle result fetching and ordering with or without cursor values.
 * @param TRow The row type that is being paginated
 * @param TSortEnum The sort enum type
 * @param TColType The cursor row's sort column type
 * @since 2.0.0
 */
interface RowPagination<TRow: StandardRow, TSortEnum: Enum<TSortEnum>, TColType> {
	/**
	 * Pagination results
	 * @param TRow The type of row objects
	 * @param TColType The cursor row's sort column type
	 * @since 2.0.0
	 */
	class Results<TRow: StandardRow, TSortEnum: Enum<TSortEnum>, TColType>(
		/**
		 * The results
		 * @since 2.0.0
		 */
		val results: List<TRow>,

		/**
		 * The pagination values for the next page, or null if no next page is available
		 * @since 2.0.0
		 */
		val prevPage: RowPagination<TRow, TSortEnum, TColType>?,

		/**
		 * The pagination values for the previous page, or null if no previous page is available
		 * @since 2.0.0
		 */
		val nextPage: RowPagination<TRow, TSortEnum, TColType>?
	): SuspendJsonSerializable {
		/**
		 * Generates a JSON representation of the pagination data
		 * @return The JSON representation
		 * @since 2.0.0
		 */
		override suspend fun toJson() = jsonObjectOf(
			"results" to if(results is JsonSerializable) results.toJson() else results,
			"prevToken" to prevPage?.toToken(),
			"nextToken" to nextPage?.toToken()
		)
	}

	/**
	 * The pagination sorting type
	 * @since 2.0.0
	 */
	val sortType: TSortEnum

	/**
	 * Whether results are sorted by descending order
	 * @since 2.0.0
	 */
	val isSortedByDesc: Boolean

	/**
	 * Whether the cursor is for a previous page.
	 * Has no effect is [columnValue] or [internalId] are null.
	 * @since 2.0.0
	 */
	val isPreviousCursor: Boolean

	/**
	 * The cursor row's internal ID, or null for no cursor
	 * @since 2.0.0
	 */
	val internalId: Int?

	/**
	 * The cursor row's sort column value, or null for no cursor
	 * @since 2.0.0
	 */
	val columnValue: TColType?

	/**
	 * Fetches results from a query, applying pagination and reversing results if necessary.
	 * @param query The query
	 * @param mapper The row mapper used to convert [Row] objects into [TRow]
	 * @param limit The maximum number of results to fetch (defaults to [API_MAX_RESULT_LIMIT])
	 * @return The paginated query results
	 */
	suspend fun fetch(query: SelectQuery<*>, mapper: (row: Row) -> TRow, limit: Int = API_MAX_RESULT_LIMIT): Results<TRow, TSortEnum, TColType>

	/**
	 * Generates a serialized token representing the pagination info
	 * @return The serialized token
	 * @since 2.0.0
	 */
	suspend fun toToken(): String
}