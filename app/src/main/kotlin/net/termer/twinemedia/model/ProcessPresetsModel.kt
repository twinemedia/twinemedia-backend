package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.model.pagination.ProcessPresetPagination
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.db.*
import org.jooq.Query
import org.jooq.UpdateQuery
import net.termer.twinemedia.util.db.Database.Sql
import org.jooq.Condition
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
		override var whereInternalIdIs: Option<Int> = none(),
		override var whereIdIs: Option<String> = none(),
		override var whereCreatedBefore: Option<OffsetDateTime> = none(),
		override var whereCreatedAfter: Option<OffsetDateTime> = none(),
		override var whereModifiedBefore: Option<OffsetDateTime> = none(),
		override var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches rows where the owner's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereOwnerInternalIdIs: Option<Int> = none(),

		/**
		 * Matches rows where the MIME type matches this SQL LIKE pattern.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereMimeIsLike: Option<String> = none(),

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
	): StandardFilters("process_presets", "preset") {
		override fun genConditions(): MutableList<Condition> {
			val res = genStandardConditions()

			val prefix = "$table.$colPrefix"

			if(whereOwnerInternalIdIs is Some)
				res.add(field("${prefix}_owner").eq((whereOwnerInternalIdIs as Some).value))
			if(whereMimeIsLike is Some)
				res.add(field("${prefix}_mime").like((whereMimeIsLike as Some).value))
			if(whereMatchesQuery is Some) {
				res.addAll(genFulltextSearchConditions(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchName)
							add("${prefix}_name")
					}
				))
			}

			return res
		}

		override fun setWithRequest(req: HttpServerRequest) {
			setStandardFiltersWithRequest(req)

			val params = req.params()

			if(params.contains("whereMimeIsLike"))
				whereMimeIsLike = some(params["whereMimeIsLike"])
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
			fun set(name: String, fieldVal: Option<*>, prefix: String = "preset_") {
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
	 * Generates context filter conditions
	 * @param type The context filter type
	 * @return The conditions
	 */
	private fun genContextFilterConditions(type: ContextFilterType): MutableList<Condition> {
		return genGenericPermissionOwnerContextConditions(type, "process_presets", "process_presets.preset_owner", context?.account?.selfAccount?.excludeOtherProcessPresets)
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
			field("account_id").`as`("preset_owner_id"),
			field("account_name").`as`("preset_owner_name"),
			field("preset_created_ts"),
			field("preset_modified_ts")
		)
			.from(table("process_presets"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq(field("preset_owner")))
			.query

	/**
	 * Creates a new process preset row with the provided details
	 * @param name The name of the new preset
	 * @param mime The MIME type (supports asterisk wildcards) that this preset applies to
	 * @param settings The preset's settings
	 * @param ownerInternalId The preset owner's internal ID
	 * @return The newly created process preset row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		name: String,
		mime: String,
		settings: JsonObject,
		ownerInternalId: Int
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("process_presets"),
			field("preset_id"),
			field("preset_name"),
			field("preset_mime"),
			field("preset_settings"),
			field("preset_owner")
		)
			.values(
				id,
				name,
				mime,
				settings,
				ownerInternalId
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
	 * @since 2.0.0
	 */
	suspend fun fetchManyDtos(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT
	): List<ProcessPresetDto> {
		val query = infoQuery()

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())
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
	 * @since 2.0.0
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: ProcessPresetPagination<TColType>,
		limit: Int,
		filters: Filters = Filters(),
	): RowPagination.Results<ProcessPresetDto, SortOrder, TColType> {
		val query = infoQuery()

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())

		return query.fetchPaginatedAsync(pagination, limit) { ProcessPresetDto.fromRow(it) }
	}

	/**
	 * Fetches one process preset's info DTO.
	 * Use [fetchManyDtos] to fetch multiple presets.
	 * @param filters Additional filters to apply
	 * @return The process preset DTO, or null if there was no result
	 * @since 2.0.0
	 */
	suspend fun fetchOneDto(filters: Filters = Filters()): ProcessPresetDto? {
		val query = infoQuery()

		query.addConditions(genContextFilterConditions(ContextFilterType.VIEW))
		query.addConditions(filters.genConditions())
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
	 * @since 2.0.0
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

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ProcessPresetRow.fromRow(it) }
	}

	/**
	 * Fetches one process preset row.
	 * Use [fetchManyRows] to fetch many presets.
	 * @param filters Additional filters to apply
	 * @return The process preset row, or null if there was no result
	 * @since 2.0.0
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): ProcessPresetRow? {
		val query =
			Sql.select(asterisk())
				.from(table("process_presets"))
				.query

		query.addConditions(genContextFilterConditions(ContextFilterType.VIEW))
		query.addConditions(filters.genConditions())
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ProcessPresetRow.fromRow(row)
	}

	/**
	 * Counts rows, taking into account any filters
	 * @param filters Additional filters to apply
	 * @return The row count
	 * @since 2.0.0
	 */
	suspend fun count(filters: AccountsModel.Filters = AccountsModel.Filters()): Int {
		val query =
			Sql.selectCount()
				.from("process_presets")
				.query

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())

		return query.fetchOneAwait()!!.getInteger("count")
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

		query.addConditions(genContextFilterConditions(ContextFilterType.UPDATE))
		query.addConditions(filters.genConditions())
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("preset_modified_ts"), now())

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

		query.addConditions(genContextFilterConditions(ContextFilterType.DELETE))
		query.addConditions(filters.genConditions())
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
