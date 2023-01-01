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
import org.jooq.Query
import org.jooq.UpdateQuery
import net.termer.twinemedia.util.db.Database.Sql
import org.jooq.Condition
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
		override var whereInternalIdIs: Option<Int> = none(),
		override var whereIdIs: Option<String> = none(),
		override var whereCreatedBefore: Option<OffsetDateTime> = none(),
		override var whereCreatedAfter: Option<OffsetDateTime> = none(),
		override var whereModifiedBefore: Option<OffsetDateTime> = none(),
		override var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches rows where the creator's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereCreatorInternalIdIs: Option<Int> = none(),

		/**
		 * Matches rows where the type is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereTypeIs: Option<ListType> = none(),

		/**
		 * Matches rows where the visibility is this.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereVisibilityIs: Option<ListVisibility> = none(),

		/**
		 * Matches rows that have fewer files than this.
		 * Using this filter automatically excludes lists of type [ListType.AUTOMATICALLY_POPULATED].
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereFileCountLessThan: Option<Int> = none(),

		/**
		 * Matches rows that have more files than this.
		 * Using this filter automatically excludes lists of type [ListType.AUTOMATICALLY_POPULATED].
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
		var querySearchName: Boolean = true,

		/**
		 * Whether [whereMatchesQuery] should search descriptions
		 * @since 2.0.0
		 */
		var querySearchDescription: Boolean = true
	): StandardFilters("lists", "list") {
		override fun genConditions(): MutableList<Condition> {
			val res = genStandardConditions()

			val prefix = "$table.$colPrefix"

			if(whereTypeIs is Some)
				res.add(field("${prefix}_type").eq((whereTypeIs as Some).value.ordinal))
			if(whereVisibilityIs is Some)
				res.add(field("${prefix}_visibility").eq((whereVisibilityIs as Some).value.ordinal))
			if(whereCreatorInternalIdIs is Some)
				res.add(field("${prefix}_creator").eq((whereCreatorInternalIdIs as Some).value))
			if(whereFileCountLessThan is Some)
				res.add(field("${prefix}_file_count").lt((whereFileCountLessThan as Some).value))
			if(whereFileCountMoreThan is Some)
				res.add(field("${prefix}_file_count").gt((whereFileCountMoreThan as Some).value))
			if(whereMatchesQuery is Some) {
				res.addAll(genFulltextSearchConditions(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchName)
							add("${prefix}_name")
						if(querySearchDescription)
							add("${prefix}_description")
					}
				))
			}

			return res
		}

		override fun setWithRequest(req: HttpServerRequest) {
			setStandardFiltersWithRequest(req)

			val params = req.params()

			if(params.contains("whereTypeIs"))
				whereTypeIs = intToListType(params["whereTypeIs"].toIntOrNull() ?: -1).orNone()
			if(params.contains("whereVisibilityIs"))
				whereVisibilityIs = intToListVisibility(params["whereVisibilityIs"].toIntOrNull() ?: -1).orNone()
			if(params.contains("whereFileCountLessThan"))
				whereFileCountLessThan = some(params["whereFileCountLessThan"].toIntOr(Int.MAX_VALUE))
			if(params.contains("whereFileCountMoreThan"))
				whereFileCountMoreThan = some(params["whereFileCountMoreThan"].toIntOr(0))
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
			fun set(name: String, fieldVal: Option<*>, prefix: String = "list_") {
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
	 * Generates context filter conditions
	 * @param type The context filter type
	 * @return The conditions
	 */
	private fun genContextFilterConditions(type: ContextFilterType): MutableList<Condition> {
		if(!ignoreContext) {
			if(context == null) {
				// If there is no context, only show lists that are set to public
				return arrayListOf(field("lists.list_visibility").eq(ListVisibility.PUBLIC.ordinal))
			} else {
				val acc = context!!.account
				val perm = "lists.${type.toPermissionVerb()}.all"

				return if(!acc.hasPermission(perm) || context!!.account.excludeOtherLists) {
					val cond = field("lists.list_creator").eq(acc.internalId)

					// Show public lists if not a listing query
					arrayListOf(if(type == ContextFilterType.VIEW)
						cond.or(field("lists.list_visibility").eq(ListVisibility.PUBLIC.ordinal))
					else
						cond
					)
				} else {
					ArrayList(0)
				}
			}
		} else {
			return ArrayList(0)
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
			.leftJoin(table("accounts")).on(field("accounts.id").eq(field("list_creator")))
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

	/**
	 * Creates a list item row.
	 * The referenced list should not be of type [ListType.AUTOMATICALLY_POPULATED], or unexpected behavior will occur.
	 * @param listInternalId The list's internal ID
	 * @param fileInternalId The file's internal ID
	 * @since 2.0.0
	 */
	suspend fun createListItemRow(listInternalId: Int, fileInternalId: Int) {
		Sql.insertInto(
			table("list_items"),
			field("item_list", listInternalId),
			field("item_file", fileInternalId)
		).executeAwait()
	}

	private fun handleCheckForFileId(query: SelectQuery<*>, checkForFileId: String?) {
		if(checkForFileId == null)
			return

		query.addSelect(
			field(
				Sql.selectCount()
					.from("list_items")
					.join(table("files")).on(field("files.id").eq(field("item_file")))
					.where(field("item_list").eq(field("lists.id")))
					.and(field("file_id").eq(checkForFileId))
			).eq(1)
				.`as`("list_contains_file")
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
	 * @since 2.0.0
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

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())
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
	 * @since 2.0.0
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: ListPagination<TColType>,
		limit: Int,
		filters: Filters = Filters(),
		checkForFileId: String? = null
	): RowPagination.Results<ListDto, SortOrder, TColType> {
		val query = infoQuery()

		handleCheckForFileId(query, checkForFileId)

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())

		return query.fetchPaginatedAsync(pagination, limit) { ListDto.fromRow(it) }
	}

	/**
	 * Fetches one list's info DTO.
	 * Use [fetchManyDtos] to fetch multiple lists.
	 * @param filters Additional filters to apply
	 * @param checkForFileId The file ID to check for in returned lists, or null to not check (defaults to null)
	 * @return The list DTO, or null if there was no result
	 * @since 2.0.0
	 */
	suspend fun fetchOneDto(filters: Filters = Filters(), checkForFileId: String?): ListDto? {
		val query = infoQuery()

		handleCheckForFileId(query, checkForFileId)

		query.addConditions(genContextFilterConditions(ContextFilterType.VIEW))
		query.addConditions(filters.genConditions())
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
	 * @since 2.0.0
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

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { ListRow.fromRow(it) }
	}

	/**
	 * Fetches one list row.
	 * Use [fetchManyRows] to fetch many lists.
	 * @param filters Additional filters to apply
	 * @return The list row, or null if there was no result
	 * @since 2.0.0
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): ListRow? {
		val query =
			Sql.select(field("lists.*"))
				.from(table("lists"))
				.query

		query.addConditions(genContextFilterConditions(ContextFilterType.VIEW))
		query.addConditions(filters.genConditions())
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			ListRow.fromRow(row)
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
				.from("lists")
				.query

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())

		return query.fetchOneAwait()!!.getInteger("count")
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

		query.addConditions(genContextFilterConditions(ContextFilterType.UPDATE))
		query.addConditions(filters.genConditions())
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("list_modified_ts"), now())

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

		query.addConditions(genContextFilterConditions(ContextFilterType.DELETE))
		query.addConditions(filters.genConditions())
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
