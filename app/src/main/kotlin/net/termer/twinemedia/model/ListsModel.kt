@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.twinemedia.model.pagination.ListPagination
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
 * Database model for lists
 * @since 1.2.0
 */
class ListsModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [ListsModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = ListsModel(null, true)
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
		 * List name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY,

		/**
		 * The number of items the list has
		 * @since 2.0.0
		 */
		ITEM_COUNT
	}

	/**
	 * Filters for fetching lists
	 * @since 2.0.0
	 */
	class Filters(
		/**
		 * Matches lists where the sequential internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereInternalIdIs: Option<Int> = none(),

		/**
		 * Matches lists where the alphanumeric ID is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereIdIs: Option<String> = none(),

		/**
		 * Matches lists where the creator's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereCreatorInternalIdIs: Option<Int> = none(),

		/**
		 * Matches lists where the type is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereTypeIs: Option<ListType> = none(),

		/**
		 * Matches lists where the visibility is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereVisibilityIs: Option<ListVisibility> = none(),

		/**
		 * Matches lists created before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches lists created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches lists modified before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches lists created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches lists where their values match this plaintext query.
		 * Search fields can be enabled by setting querySearch* properties to true.
		 *
		 * @since 2.0.0
		 */
		var whereMatchesQuery: Option<String> = none(),

		/**
		 * Whether [whereMatchesQuery] should search list names
		 * @since 2.0.0
		 */
		var querySearchName: Boolean = true,

		/**
		 * Whether [whereMatchesQuery] should search list descriptions
		 * @since 2.0.0
		 */
		var querySearchDescription: Boolean = true
	): Model.Filters {
		override fun applyTo(query: ConditionProvider) {
			if(whereInternalIdIs is Some)
				query.addConditions(field("lists.id").eq((whereInternalIdIs as Some).value))
			if(whereIdIs is Some)
				query.addConditions(field("lists.list_id").eq((whereIdIs as Some).value))
			if(whereTypeIs is Some)
				query.addConditions(field("lists.list_type").eq((whereTypeIs as Some).value.ordinal))
			if(whereVisibilityIs is Some)
				query.addConditions(field("lists.list_visibility").eq((whereVisibilityIs as Some).value.ordinal))
			if(whereCreatorInternalIdIs is Some)
				query.addConditions(field("lists.list_creator").eq((whereCreatorInternalIdIs as Some).value))
			if(whereCreatedBefore is Some)
				query.addConditions(field("lists.list_created_ts").gt((whereCreatedBefore as Some).value))
			if(whereCreatedAfter is Some)
				query.addConditions(field("lists.list_created_ts").gt((whereCreatedAfter as Some).value))
			if(whereModifiedBefore is Some)
				query.addConditions(field("lists.list_modified_ts").gt((whereModifiedBefore as Some).value))
			if(whereModifiedAfter is Some)
				query.addConditions(field("lists.list_modified_ts").gt((whereModifiedAfter as Some).value))
			if(whereMatchesQuery is Some) {
				query.addFulltextSearchCondition(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchName)
							add("lists.list_name")
						if(querySearchDescription)
							add("lists.list_description")
					}
				)
			}
		}

		override fun setWithRequest(req: HttpServerRequest) {
			val params = req.params()

			if(params.contains("whereTypeIs"))
				whereTypeIs = intToListType(params["whereTypeIs"].toIntOrNull() ?: -1).orNone()
			if(params.contains("whereVisibilityIs"))
				whereVisibilityIs = intToListVisibility(params["whereVisibilityIs"].toIntOrNull() ?: -1).orNone()
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
				querySearchDescription = params["querySearchDescription"] == "true"
			}
		}

	}

	/**
	 * Values to update on list rows
	 * @since 2.0.0
	 */
	class UpdateValues(
		/**
		 * Name
		 * @since 2.0.0
		 */
		var name: Option<String> = none(),

		/**
		 * Description
		 * @since 2.0.0
		 */
		var description: Option<String> = none(),

		/**
		 * List type
		 * @since 2.0.0
		 */
		var type: Option<ListType> = none(),

		/**
		 * List visibility
		 * @since 2.0.0
		 */
		var visibility: Option<ListVisibility> = none(),

		/**
		 * The tags that files must have to be in this list.
		 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], should be [None] for other types.
		 * @since 2.0.0
		 */
		val sourceTags: Option<Array<String>>,

		/**
		 * The tags that files must NOT have to be in this list.
		 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], should be [None] for other types.
		 * @since 2.0.0
		 */
		val sourceExcludeTags: Option<Array<String>>,

		/**
		 * The MIME type files must have to be in this list.
		 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], should be [None] for other types.
		 * @since 2.0.0
		 */
		val sourceMime: Option<String>,

		/**
		 * The time files must have been uploaded before to be in this list.
		 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], should be [None] for other types.
		 * @since 2.0.0
		 */
		val sourceCreatedBefore: Option<OffsetDateTime>,

		/**
		 * The time files must have been uploaded after to be in this list.
		 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], should be [None] for other types.
		 * @since 2.0.0
		 */
		val sourceCreatedAfter: Option<OffsetDateTime>,

		/**
		 * Whether files by all users should be shown in list, not just by the list creator.
		 * Only applies to lists with type [ListType.AUTOMATICALLY_POPULATED], should be [None] for other types.
		 * @since 2.0.0
		 */
		val showAllUserFiles: Option<Boolean>
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "lists.list_") {
				if(fieldVal is Some)
					query.addValue(
						field(prefix + name),
						when(fieldVal.value) {
							is Array<*> -> array(*fieldVal.value)
							is Enum<*> -> fieldVal.value.ordinal
							else -> fieldVal.value
						}
					)
			}

			set("name", name)
			set("description", description)
			set("type", type)
			set("visibility", visibility)
			set("source_tags", sourceTags)
			set("source_exclude_tags", sourceExcludeTags)
			set("source_mime", sourceMime)
			set("source_created_before", sourceCreatedBefore)
			set("source_created_after", sourceCreatedAfter)
			set("show_all_user_files", showAllUserFiles)
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
			SortOrder.CREATED_TS -> orderBy("lists.list_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("lists.list_modified_ts")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("lists.list_name")
			SortOrder.ITEM_COUNT -> orderBy("lists.list_item_count")
		}

		return this
	}

	/**
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 */
	private fun applyContextFilters(query: ConditionProvider) {
		if(!ignoreContext) {
			if(context == null) {
				// If there is no context, only show lists that are set to public
				query.addConditions(field("lists.list_visibility").eq(ListVisibility.PUBLIC.ordinal))
			} else if(!context!!.account.hasPermission("lists.list.all") || context!!.account.excludeOtherLists) {
				// If the account does not have lists.list.all, or is excluding lists by other accounts, only show lists by the account
				query.addConditions(field("lists.list_creator").eq(context!!.account.internalId))
			}
		}
	}

	/**
	 * Generates a query for getting DTO info
	 * @return The query
	 */
	private fun infoQuery() =
		Sql.select(
			field("lists.id"),
			field("list_name"),
			field("list_description"),
			field("account_id").`as`("list_creator_id"),
			field("account_name").`as`("list_creator_name"),
			field("list_type"),
			field("list_visibility"),
			field("list_source_tags"),
			field("list_source_exclude_tags"),
			field("list_source_mime"),
			field("list_source_created_before"),
			field("list_source_created_after"),
			field("list_show_all_user_files"),
			field("list_item_count"),
			field("list_created_ts"),
			field("list_modified_ts")
		)
			.from(table("lists"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq("list_creator"))
			.query

	/**
	 * Creates a new list row with the provided details
	 * @param name The name of the new list
	 * @param description The list's description (defaults to an empty string)
	 * @param creatorInternalId The list creator's internal ID
	 * @param type The list type
	 * @param visibility The list visibility
	 * @param sourceTags The tags that files must have to be in this list (value will be ignored if type is not [ListType.AUTOMATICALLY_POPULATED])
	 * @param sourceExcludeTags The tags that files must NOT have to be in this list (value will be ignored if type is not [ListType.AUTOMATICALLY_POPULATED])
	 * @param sourceMime The MIME type files must have to be in this list (value will be ignored if type is not [ListType.AUTOMATICALLY_POPULATED])
	 * @param sourceCreatedBefore The time files must have been uploaded before to be in this list (value will be ignored if type is not [ListType.AUTOMATICALLY_POPULATED])
	 * @param sourceCreatedAfter The time files must have been uploaded after to be in this list (value will be ignored if type is not [ListType.AUTOMATICALLY_POPULATED])
	 * @param showAllAccountFiles Whether files by all accounts should be shown in list, not just by the list creator (value will be ignored if type is not [ListType.AUTOMATICALLY_POPULATED])
	 * @return The newly created list row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		name: String,
		description: String,
		creatorInternalId: Int,
		type: ListType,
		visibility: ListVisibility,
		sourceTags: Array<String>?,
		sourceExcludeTags: Array<String>?,
		sourceMime: String?,
		sourceCreatedBefore: OffsetDateTime?,
		sourceCreatedAfter: OffsetDateTime?,
		showAllAccountFiles: Boolean
	): RowIdPair {
		val id = genRowId()

		var insertValuesStep = Sql.insertInto(
			table("lists"),
			field("list_id"),
			field("list_name"),
			field("list_description"),
			field("list_creator"),
			field("list_type"),
			field("list_visibility"),
			field("list_source_tags"),
			field("list_source_exclude_tags"),
			field("list_source_mime"),
			field("list_source_created_before"),
			field("list_source_created_after"),
			field("list_show_all_user_files"),
			field("list_item_count")
		)

		// Make sure correct values for the specified type are inserted
		when(type) {
			ListType.STANDARD -> insertValuesStep = insertValuesStep.values(
				id,
				name,
				description,
				creatorInternalId,
				type,
				visibility,
				null,
				null,
				null,
				null,
				null,
				false,
				0
			)
			ListType.AUTOMATICALLY_POPULATED -> insertValuesStep = insertValuesStep.values(
				id,
				name,
				description,
				creatorInternalId,
				type,
				visibility,
				sourceTags,
				sourceExcludeTags,
				sourceMime,
				sourceCreatedBefore,
				sourceCreatedAfter,
				showAllAccountFiles,
				null
			)
		}

		val internalId = insertValuesStep
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	private fun handleCheckForFileId(query: SelectQuery<*>, checkForFileId: String?) {
		if(checkForFileId == null)
			return

		query.addSelect(
			field(
				Sql.selectCount()
					.from("list_items")
					.join(table("files")).on(field("files.id").eq("item_file"))
					.where(field("item_list").eq(field("lists.id")))
					.and(field("file_id").eq(checkForFileId))
			).eq(1)
				.`as`("contains_file")
		)
	}

	/**
	 * Fetches many lists' info DTOs.
	 * Use [fetchOneDto] to fetch only one list.
	 * @param filters Additional filters to apply
	 * @param order Which order to sort results with (defaults to [SortOrder.CREATED_TS])
	 * @param orderDesc Whether to sort results in descending order (defaults to false)
	 * @param limit The number of results to return (defaults to [API_MAX_RESULT_LIMIT])
	 * @param checkForFileId The file ID to check for in returned lists, or null to not check (defaults to null)
	 * @return The results
	 */
	suspend fun fetchManyDtos(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT,
		checkForFileId: String?
	): List<ListDto> {
		val query = infoQuery()

		handleCheckForFileId(query, checkForFileId)

		applyContextFilters(query)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ListDto.fromRow(it) }
	}

	/**
	 * Fetches many lists' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one list.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @param checkForFileId The file ID to check for in returned lists, or null to not check (defaults to null)
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: ListPagination<TColType>,
		limit: Int,
		filters: Filters = Filters(),
		checkForFileId: String? = null
	): RowPagination.Results<ListDto, SortOrder, TColType> {
		val query = infoQuery()

		handleCheckForFileId(query, checkForFileId)

		applyContextFilters(query)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { ListDto.fromRow(it) }
	}

	/**
	 * Fetches one list's info DTO.
	 * Use [fetchManyDtos] to fetch multiple lists.
	 * @param filters Additional filters to apply
	 * @param checkForFileId The file ID to check for in returned lists, or null to not check (defaults to null)
	 * @return The list DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(filters: Filters = Filters(), checkForFileId: String?): ListDto? {
		val query = infoQuery()

		handleCheckForFileId(query, checkForFileId)

		applyContextFilters(query)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ListDto.fromRow(row)
	}

	/**
	 * Fetches many list rows.
	 * Use [fetchOneRow] to fetch only one list.
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
	): List<ListRow> {
		val query =
			Sql.select(asterisk())
				.from(table("lists"))
				.query

		applyContextFilters(query)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ListRow.fromRow(it) }
	}

	/**
	 * Fetches one list row.
	 * Use [fetchManyRows] to fetch many lists.
	 * @param filters Additional filters to apply
	 * @return The list row, or null if there was no result
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): ListRow? {
		val query =
			Sql.select(field("lists.*"))
				.from(table("lists"))
				.query

		applyContextFilters(query)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ListRow.fromRow(row)
	}

	/**
	 * Updates many list rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the lists' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("lists"))

		applyContextFilters(query)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("lists.list_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates a list row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the list's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many list rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("lists"))

		applyContextFilters(query)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one list row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}