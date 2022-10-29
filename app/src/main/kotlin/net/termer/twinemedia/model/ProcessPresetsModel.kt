@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.model.pagination.ProcessPresetPagination
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
 * Database model for process presets
 * @since 2.0.0
 */
class ProcessPresetsModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [ProcessPresetsModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = ProcessPresetsModel(null, true)
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
		 * Preset name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY,

		/**
		 * Preset MIME alphabetically
		 * @since 2.0.0
		 */
		MIME_ALPHABETICALLY
	}

	/**
	 * Filters for fetching presets
	 * @since 2.0.0
	 */
	class Filters(
		/**
		 * Matches presets where the sequential internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereInternalIdIs: Option<Int> = none(),

		/**
		 * Matches presets where the alphanumeric ID is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereIdIs: Option<String> = none(),

		/**
		 * Matches presets where the creator's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereCreatorInternalIdIs: Option<Int> = none(),

		/**
		 * Matches presets where the MIME type matches this SQL LIKE pattern.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereMimeIsLike: Option<String> = none(),

		/**
		 * Matches presets created before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches presets created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches presets modified before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches presets modified after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches presets where their values match this plaintext query.
		 * Search fields can be enabled by setting querySearch* properties to true.
		 *
		 * @since 2.0.0
		 */
		var whereMatchesQuery: Option<String> = none(),

		/**
		 * Whether [whereMatchesQuery] should search preset names
		 * @since 2.0.0
		 */
		var querySearchName: Boolean = true
	): Model.Filters {
		override fun applyTo(query: ConditionProvider) {
			if(whereInternalIdIs is Some)
				query.addConditions(field("process_presets.id").eq((whereInternalIdIs as Some).value))
			if(whereIdIs is Some)
				query.addConditions(field("process_presets.process_id").eq((whereIdIs as Some).value))
			if(whereCreatorInternalIdIs is Some)
				query.addConditions(field("process_presets.preset_creator").eq((whereCreatorInternalIdIs as Some).value))
			if(whereMimeIsLike is Some)
				query.addConditions(field("process_presets.preset_mime").like((whereMimeIsLike as Some).value))
			if(whereCreatedBefore is Some)
				query.addConditions(field("process_presets.preset_created_ts").lt((whereCreatedBefore as Some).value))
			if(whereCreatedAfter is Some)
				query.addConditions(field("process_presets.preset_created_ts").gt((whereCreatedAfter as Some).value))
			if(whereModifiedBefore is Some)
				query.addConditions(field("process_presets.preset_modified_ts").lt((whereModifiedBefore as Some).value))
			if(whereModifiedAfter is Some)
				query.addConditions(field("process_presets.preset_modified_ts").gt((whereModifiedAfter as Some).value))
			if(whereMatchesQuery is Some) {
				query.addFulltextSearchCondition(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchName)
							add("process_presets.preset_name")
					}
				)
			}
		}

		override fun setWithRequest(req: HttpServerRequest) {
			val params = req.params()

			if(params.contains("whereMimeIsLike"))
				whereMimeIsLike = some(params["whereMimeIsLike"])
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
	 * Values to update on process preset rows
	 * @since 2.0.0
	 */
	class UpdateValues(
		/**
		 * Name
		 * @since 2.0.0
		 */
		var name: Option<String> = none(),

		/**
		 * MIME type (supports asterisk wildcards) that this preset applies to
		 * @since 2.0.0
		 */
		var mime: Option<String> = none(),

		/**
		 * Settings
		 * @since 2.0.0
		 */
		val settings: Option<JsonObject>,

		/**
		 * Output file extension
		 * @since 2.0.0
		 */
		val extension: Option<String>
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "process_presets.preset_") {
				if(fieldVal is Some)
					query.addValue(
						field(prefix + name),
						when(fieldVal.value) {
							is JsonObject -> fieldVal.value.encode()
							else -> fieldVal.value
						}
					)
			}

			set("name", name)
			set("mime", mime)
			set("settings", settings)
			set("extension", extension)
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
			SortOrder.CREATED_TS -> orderBy("process_presets.preset_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("process_presets.preset_modified_ts")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("process_presets.preset_name")
			SortOrder.MIME_ALPHABETICALLY -> orderBy("process_presets.preset_mime")
		}

		return this
	}

	/**
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 * @param type The context filter type
	 */
	private fun applyContextFilters(query: ConditionProvider, type: ContextFilterType) {
		applyGenericPermissionCreatorContextFilter(query, type, "process_presets", "process_presets.preset_creator", context?.account?.excludeOtherProcessPresets)
	}

	/**
	 * Generates a query for getting DTO info
	 * @return The query
	 */
	private fun infoQuery() =
		Sql.select(
			field("process_presets.id"),
			field("preset_name"),
			field("preset_mime"),
			field("preset_settings"),
			field("""trim('"' FROM (process_settings::jsonb->'extension')::text)""").`as`("preset_extension"), // Extract extension from JSON
			field("account_id").`as`("preset_creator_id"),
			field("account_name").`as`("preset_creator_name"),
			field("preset_created_ts"),
			field("preset_modified_ts")
		)
			.from(table("process_presets"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq("preset_creator"))
			.query

	/**
	 * Creates a new process preset row with the provided details
	 * @param name The name of the new preset
	 * @param mime The MIME type (supports asterisk wildcards) that this preset applies to
	 * @param settings The preset's settings
	 * @param creatorInternalId The preset creator's internal ID
	 * @return The newly created process preset row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		name: String,
		mime: String,
		settings: JsonObject,
		creatorInternalId: Int
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("process_presets"),
			field("preset_id"),
			field("preset_name"),
			field("preset_mime"),
			field("preset_settings"),
			field("preset_creator")
		)
			.values(
				id,
				name,
				mime,
				settings,
				creatorInternalId
			)
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Fetches many process presets' info DTOs.
	 * Use [fetchOneDto] to fetch only one preset.
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
	): List<ProcessPresetDto> {
		val query = infoQuery()

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ProcessPresetDto.fromRow(it) }
	}

	/**
	 * Fetches many process presets' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one preset.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: ProcessPresetPagination<TColType>,
		limit: Int,
		filters: Filters = Filters(),
	): RowPagination.Results<ProcessPresetDto, SortOrder, TColType> {
		val query = infoQuery()

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { ProcessPresetDto.fromRow(it) }
	}

	/**
	 * Fetches one process preset's info DTO.
	 * Use [fetchManyDtos] to fetch multiple presets.
	 * @param filters Additional filters to apply
	 * @return The process preset DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(filters: Filters = Filters()): ProcessPresetDto? {
		val query = infoQuery()

		applyContextFilters(query, ContextFilterType.VIEW)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ProcessPresetDto.fromRow(row)
	}

	/**
	 * Fetches many process preset rows.
	 * Use [fetchOneRow] to fetch only one preset.
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
	): List<ProcessPresetRow> {
		val query =
			Sql.select(asterisk())
				.from(table("process_presets"))
				.query

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ProcessPresetRow.fromRow(it) }
	}

	/**
	 * Fetches one process preset row.
	 * Use [fetchManyRows] to fetch many presets.
	 * @param filters Additional filters to apply
	 * @return The process preset row, or null if there was no result
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): ProcessPresetRow? {
		val query =
			Sql.select(asterisk())
				.from(table("process_presets"))
				.query

		applyContextFilters(query, ContextFilterType.VIEW)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ProcessPresetRow.fromRow(row)
	}

	/**
	 * Updates many process preset rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the presets' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("process_presets"))

		applyContextFilters(query, ContextFilterType.UPDATE)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("process_presets.preset_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates a process preset row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the preset's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many process preset rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("process_presets"))

		applyContextFilters(query, ContextFilterType.DELETE)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one process preset row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}