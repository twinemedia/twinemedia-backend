@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.model.pagination.ApiKeyPagination
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.db.*
import org.jooq.ConditionProvider
import org.jooq.Query
import org.jooq.UpdateQuery
import net.termer.twinemedia.util.db.Database.Sql
import org.jooq.impl.DSL.*
import java.time.OffsetDateTime

/**
 * Database model for API keys
 * @since 1.2.0
 */
class ApiKeysModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [ApiKeysModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = ApiKeysModel(null, true)
	}

	/**
	 * Sorting orders
	 * @since 2.0.0
	 */
	enum class SortOrder {
		/**
		 * Created timestamp
		 * @since 2.0.0
		 */
		CREATED_TS,

		/**
		 * Modified timestamp
		 * @since 2.0.0
		 */
		MODIFIED_TS,

		/**
		 * API key name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY
	}

	/**
	 * Filters for fetching API keys
	 * @since 2.0.0
	 */
	class Filters(
		/**
		 * Matches API keys where the sequential internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereInternalIdIs: Option<Int> = none(),

		/**
		 * Matches API keys where the alphanumeric ID is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereIdIs: Option<String> = none(),

		/**
		 * Matches API keys where the creator's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereCreatorIdIs: Option<Int> = none(),

		/**
		 * Matches API keys created before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches API keys created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches API keys modified before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches API keys created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches API keys where their values match this plaintext query.
		 * Search fields can be enabled by setting querySearch* properties to true.
		 *
		 * @since 2.0.0
		 */
		var whereMatchesQuery: Option<String> = none(),

		/**
		 * Whether [whereMatchesQuery] should search API key names
		 * @since 2.0.0
		 */
		var querySearchName: Boolean = true
	): Model.Filters {
		override fun applyTo(query: ConditionProvider) {
			if(whereInternalIdIs is Some)
				query.addConditions(field("api_keys.id").eq((whereInternalIdIs as Some).value))
			if(whereIdIs is Some)
				query.addConditions(field("api_keys.key_id").eq((whereIdIs as Some).value))
			if(whereCreatorIdIs is Some)
				query.addConditions(field("api_keys.key_creator").eq((whereCreatorIdIs as Some).value))
			if(whereCreatedBefore is Some)
				query.addConditions(field("api_keys.key_created_ts").gt((whereCreatedBefore as Some).value))
			if(whereCreatedAfter is Some)
				query.addConditions(field("api_keys.key_created_ts").gt((whereCreatedAfter as Some).value))
			if(whereModifiedBefore is Some)
				query.addConditions(field("api_keys.key_modified_ts").gt((whereModifiedBefore as Some).value))
			if(whereModifiedAfter is Some)
				query.addConditions(field("api_keys.key_modified_ts").gt((whereModifiedAfter as Some).value))
			if(whereMatchesQuery is Some) {
				query.addFulltextSearchCondition(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchName)
							add("api_keys.key_name")
					}
				)
			}
		}

		override fun setWithRequest(req: HttpServerRequest) {
			val params = req.params()

			if(params.contains("whereIdIs"))
				whereIdIs = some(params["whereIdIs"])
			if(params.contains("whereCreatedBefore"))
				whereCreatedBefore = dateStringToOffsetDateTimeOrNone(params["whereCreatedBefore"])
			if(params.contains("whereCreatedAfter"))
				whereCreatedAfter = dateStringToOffsetDateTimeOrNone(params["whereCreatedAfter"])
			if(params.contains("whereModifiedBefore"))
				whereModifiedBefore = dateStringToOffsetDateTimeOrNone(params["whereModifiedBefore"])
			if(params.contains("whereModifiedAfter"))
				whereModifiedAfter = dateStringToOffsetDateTimeOrNone(params["whereModifiedAfter"])
			if(params.contains("whereMatchesQuery")) {
				whereMatchesQuery = some(params["whereMatchesQuery"])
				querySearchName = params["querySearchName"] == "true"
			}
		}

	}

	/**
	 * Values to update on API key rows
	 * @since 2.0.0
	 */
	class UpdateValues(
		/**
		 * Name
		 * @since 2.0.0
		 */
		var name: Option<String> = none(),

		/**
		 * Permissions
		 * @since 2.0.0
		 */
		var permissions: Option<Array<String>> = none(),

		/**
		 * JSON Web Token
		 * @since 2.0.0
		 */
		var jwt: Option<String> = none()
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "api_keys.key_") {
				if(fieldVal is Some)
					query.addValue(field(prefix + name), if(fieldVal.value is Array<*>) array(*fieldVal.value) else fieldVal.value)
			}

			set("name", name)
			set("permissions", permissions)
			set("jwt", jwt)
		}
	}

	/**
	 * Orders the provided query using the specified sort order
	 * @param order The sort order
	 * @param orderDesc Whether to sort by descending order
	 * @return This, to be used fluently
	 */
	private fun Query.orderBy(order: SortOrder, orderDesc: Boolean): Query {
		fun orderBy(name: String) {
			orderBy(if(orderDesc) field(name).desc() else field(name))
		}

		when(order) {
			SortOrder.CREATED_TS -> orderBy("api_keys.key_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("api_keys.key_modified_ts")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("api_keys.key_name")
		}

		return this
	}

	/**
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 */
	private fun applyContextFilters(query: ConditionProvider) {
		// No filters to apply
		// Higher level permission checks on controllers restrict access to API key data
		// Generally, API keys are only shown for the user's account
	}

	/**
	 * Generates a query for getting DTO info
	 * @return The query
	 */
	private fun infoQuery() =
		Sql.select(
			field("api_keys.id"),
			field("key_name"),
			field("key_permissions"),
			field("key_jwt"),
			field("account_id").`as`("key_creator_id"),
			field("account_name").`as`("key_creator_name"),
			field("key_created_ts"),
			field("key_modified_ts")
		)
			.from(table("api_keys"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq("key_creator"))
			.query

	/**
	 * Creates a new API key row with the provided details
	 * @param name The name of the new API key
	 * @param permissions An array of permissions that the new API key will have
	 * @param jwt The API key's actual JWT token
	 * @param creatorId The API key creator's internal ID
	 * @return The newly created API key entry's ID
	 * @since 2.0.0
	 */
	suspend fun createApiKeyRow(
		name: String,
		permissions: Array<String>,
		jwt: String,
		creatorId: Int
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("api_keys"),
			field("key_id"),
			field("key_name"),
			field("key_permissions"),
			field("key_jwt"),
			field("key_creator")
		)
			.values(id, name, permissions, jwt, creatorId)
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Fetches many API keys' info DTOs.
	 * Use [fetchOneDto] to fetch only one API key.
	 * @param filters Additional filters to apply
	 * @param order Which order to sort results with (defaults to [SortOrder.CREATED_TS])
	 * @param orderDesc Whether to sort results in descending order (defaults to false)
	 * @param limit The number of results to return (defaults to [API_MAX_RESULT_LIMIT])
	 * @return The results
	 */
	suspend fun fetchManyDtos(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT
	): List<ApiKeyDto> {
		val query = infoQuery()

		applyContextFilters(query)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ApiKeyDto.fromRow(it) }
	}

	/**
	 * Fetches many API keys' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one API key.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: ApiKeyPagination<TColType>,
		limit: Int,
		filters: Filters = Filters()
	): RowPagination.Results<ApiKeyDto, SortOrder, TColType> {
		val query = infoQuery()

		applyContextFilters(query)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { ApiKeyDto.fromRow(it) }
	}

	/**
	 * Fetches one API key's info DTO.
	 * Use [fetchManyDtos] to fetch multiple API keys.
	 * @param filters Additional filters to apply
	 * @return The API key DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(filters: Filters = Filters()): ApiKeyDto? {
		val query = infoQuery()

		applyContextFilters(query)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ApiKeyDto.fromRow(row)
	}

	/**
	 * Fetches many API key rows.
	 * Use [fetchOneRow] to fetch only one API key.
	 * @param filters Additional filters to apply
	 * @param order Which order to sort results with (defaults to [SortOrder.CREATED_TS])
	 * @param orderDesc Whether to sort results in descending order (defaults to false)
	 * @param limit The number of results to return (defaults to [API_MAX_RESULT_LIMIT])
	 * @return The results
	 */
	suspend fun fetchManyRows(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT
	): List<ApiKeyRow> {
		val query =
			Sql.select(asterisk())
				.from(table("api_keys"))
				.query

		applyContextFilters(query)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ApiKeyRow.fromRow(it) }
	}

	/**
	 * Fetches one API key row.
	 * Use [fetchManyRows] to fetch many API keys.
	 * @param filters Additional filters to apply
	 * @return The API key row, or null if there was no result
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): ApiKeyRow? {
		val query =
			Sql.select(field("api_keys.*"))
				.from(table("api_keys"))
				.query

		applyContextFilters(query)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ApiKeyRow.fromRow(row)
	}

	/**
	 * Updates many API key rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the API keys' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("api_keys"))

		applyContextFilters(query)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("api_keys.key_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates an API key row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the API key's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many API key rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("api_keys"))

		applyContextFilters(query)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one API key row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}