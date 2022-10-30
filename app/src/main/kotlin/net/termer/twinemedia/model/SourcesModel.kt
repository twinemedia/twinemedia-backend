@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.model.pagination.SourcePagination
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.db.*
import org.jooq.ConditionProvider
import org.jooq.Query
import org.jooq.UpdateQuery
import net.termer.twinemedia.util.db.Database.Sql
import org.jooq.SelectQuery
import org.jooq.impl.DSL.*
import java.time.OffsetDateTime

/**
 * Database model for file sources
 * @since 1.2.0
 */
class SourcesModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [SourcesModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = SourcesModel(null, true)
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
		 * Source name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY,

		/**
		 * The number of files associated with the file source
		 * @since 2.0.0
		 */
		FILE_COUNT
	}

	/**
	 * Filters for fetching file sources
	 * @since 2.0.0
	 */
	class Filters(
		override var whereInternalIdIs: Option<Int> = none(),
		override var whereIdIs: Option<String> = none(),
		override var whereCreatedBefore: Option<OffsetDateTime> = none(),
		override var whereCreatedAfter: Option<OffsetDateTime> = none(),
		override var whereModifiedBefore: Option<OffsetDateTime> = none(),
		override var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches rows where the type is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereTypeIs: Option<String> = none(),

		/**
		 * Matches rows where the creator's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereCreatorInternalIdIs: Option<Int> = none(),

		/**
		 * Matches rows that have this global status.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereGlobalStatusIs: Option<Boolean> = none(),

		/**
		 * Matches rows that have fewer files than this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereFileCountLessThan: Option<Int> = none(),

		/**
		 * Matches rows that have more files than this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereFileCountMoreThan: Option<Int> = none(),

		/**
		 * Matches rows where their values match this plaintext query.
		 * Search fields can be enabled by setting querySearch* properties to true.
		 * @since 2.0.0
		 */
		var whereMatchesQuery: Option<String> = none(),

		/**
		 * Whether [whereMatchesQuery] should search names
		 * @since 2.0.0
		 */
		var querySearchName: Boolean = true
	): StandardFilters("sources", "source") {
		override fun applyTo(query: ConditionProvider) {
			applyStandardFiltersTo(query)

			val prefix = "$table.$colPrefix"

			if(whereTypeIs is Some)
				query.addConditions(field("sources.source_type").eq((whereTypeIs as Some).value))
			if(whereCreatorInternalIdIs is Some)
				query.addConditions(field("${prefix}_creator").eq((whereCreatorInternalIdIs as Some).value))
			if(whereGlobalStatusIs is Some)
				query.addConditions(field("${prefix}_global").eq((whereGlobalStatusIs as Some).value))
			if(whereFileCountLessThan is Some)
				query.addConditions(field("${prefix}_file_count").lt((whereFileCountLessThan as Some).value))
			if(whereFileCountMoreThan is Some)
				query.addConditions(field("${prefix}_file_count").gt((whereFileCountMoreThan as Some).value))
			if(whereMatchesQuery is Some) {
				query.addFulltextSearchCondition(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchName)
							add("${prefix}_name")
					}
				)
			}
		}

		override fun setWithRequest(req: HttpServerRequest) {
			setStandardFiltersWithRequest(req)

			val params = req.params()

			if(params.contains("whereTypeIs"))
				whereTypeIs = some(params["whereTypeIs"])
			if(params.contains("whereGlobalStatusIs"))
				whereGlobalStatusIs = some(params["whereGlobalStatusIs"] == "true")
			if(params.contains("whereFileCountLessThan"))
				whereFileCountLessThan = some(params["whereFileCountLessThan"].toIntOr(Int.MAX_VALUE))
			if(params.contains("whereFileCountMoreThan"))
				whereFileCountMoreThan = some(params["whereFileCountMoreThan"].toIntOr(0))
			if(params.contains("whereMatchesQuery")) {
				whereMatchesQuery = some(params["whereMatchesQuery"])
				querySearchName = params["querySearchName"] == "true"
			}
		}
	}

	/**
	 * Values to update on file source rows
	 * @since 2.0.0
	 */
	class UpdateValues(
		/**
		 * Name
		 * @since 2.0.0
		 */
		var name: Option<String> = none(),

		/**
		 * Source configuration
		 * @since 2.0.0
		 */
		var config: Option<JsonObject> = none(),

		/**
		 * Whether the file source is available to be used by all accounts
		 * @since 2.0.0
		 */
		var isGlobal: Option<Boolean> = none()
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "sources.source_") {
				if(fieldVal is Some)
					query.addValue(field(prefix + name), if(fieldVal.value is JsonObject) fieldVal.value.encode() else fieldVal.value)
			}

			set("name", name)
			set("config", config)
			set("global", isGlobal)
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
			SortOrder.CREATED_TS -> orderBy("sources.source_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("sources.source_modified_ts")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("sources.source_name")
			SortOrder.FILE_COUNT -> orderBy("sources.source_file_count")
		}

		return this
	}

	/**
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 * @param type The context filter type
	 */
	private fun applyContextFilters(query: ConditionProvider, type: ContextFilterType) {
		applyGenericPermissionCreatorContextFilter(query, type, "sources", "sources.source_creator", context?.account?.excludeOtherSources)
	}

	/**
	 * Generates a query for getting DTO info
	 * @param includeConfig Whether to include source configs
	 * @return The query
	 */
	private fun infoQuery(includeConfig: Boolean): SelectQuery<*> {
		val select = Sql.select(
			field("sources.id"),
			field("source_id"),
			field("source_type"),
			field("source_name"),
			field("account_id").`as`("source_creator_id"),
			field("account_name").`as`("source_creator_name"),
			field("source_global"),
			field("source_file_count"),
			field("source_created_ts"),
			field("source_modified_ts")
		)

		if(includeConfig)
			select.select(field("source_config"))

		return select
			.from(table("sources"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq("sources.source_creator"))
			.query
	}

	/**
	 * Creates a new file source row with the provided details
	 * @param type The source type
	 * @param name The name
	 * @param config The source config
	 * @param isGlobal Whether the file source is available to be used by all accounts
	 * @param creatorInternalId The source creator's internal ID
	 * @return The newly created file source row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		type: String,
		name: String,
		config: JsonObject,
		isGlobal: Boolean,
		creatorInternalId: Int
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("sources"),
			field("source_id"),
			field("source_type"),
			field("source_name"),
			field("source_config"),
			field("source_global"),
			field("source_creator")
		)
			.values(
				id,
				type,
				name,
				config,
				isGlobal,
				creatorInternalId
			)
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Fetches many file sources' info DTOs.
	 * Use [fetchOneDto] to fetch only one source.
	 * @param filters Additional filters to apply
	 * @param order Which order to sort results with (defaults to [SortOrder.CREATED_TS])
	 * @param orderDesc Whether to sort results in descending order (defaults to false)
	 * @param limit The number of results to return (defaults to [API_MAX_RESULT_LIMIT])
	 * @param includeConfig Whether to include source configs
	 * @return The results
	 */
	suspend fun fetchManyDtos(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT,
		includeConfig: Boolean
	): List<SourceDto> {
		val query = infoQuery(includeConfig)

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { SourceDto.fromRow(it) }
	}

	/**
	 * Fetches many file sources' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one source.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @param includeConfig Whether to include source configs
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: SourcePagination<TColType>,
		limit: Int,
		filters: Filters = Filters(),
		includeConfig: Boolean
	): RowPagination.Results<SourceDto, SortOrder, TColType> {
		val query = infoQuery(includeConfig)

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { SourceDto.fromRow(it) }
	}

	/**
	 * Fetches one file source's info DTO.
	 * Use [fetchManyDtos] to fetch multiple sources.
	 * @param filters Additional filters to apply
	 * @param includeConfig Whether to include source configs
	 * @return The file source DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(
		filters: Filters = Filters(),
		includeConfig: Boolean
	): SourceDto? {
		val query = infoQuery(includeConfig)

		applyContextFilters(query, ContextFilterType.VIEW)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			SourceDto.fromRow(row)
	}

	/**
	 * Fetches many file source rows.
	 * Use [fetchOneRow] to fetch only one source.
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
	): List<SourceRow> {
		val query =
			Sql.select(asterisk())
				.from(table("sources"))
				.query

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { SourceRow.fromRow(it) }
	}

	/**
	 * Fetches one file source row.
	 * Use [fetchManyRows] to fetch many sources.
	 * @param filters Additional filters to apply
	 * @return The source row, or null if there was no result
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): SourceRow? {
		val query =
			Sql.select(asterisk())
				.from(table("sources"))
				.query

		applyContextFilters(query, ContextFilterType.VIEW)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			SourceRow.fromRow(row)
	}

	/**
	 * Updates many file source rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the sources' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("sources"))

		applyContextFilters(query, ContextFilterType.UPDATE)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("sources.source_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates a file source row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the source's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many file source rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("sources"))

		applyContextFilters(query, ContextFilterType.DELETE)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one file source row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}