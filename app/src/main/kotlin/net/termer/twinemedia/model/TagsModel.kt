@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.model.pagination.TagPagination
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
 * Database model for tags
 * @since 1.2.0
 */
class TagsModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [TagsModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = TagsModel(null, true)
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
		 * Tag name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY,

		/**
		 * The number of files that have the tag
		 * @since 2.0.0
		 */
		FILE_COUNT
	}

	/**
	 * Filters for fetching tags
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
		var querySearchName: Boolean = true,

		/**
		 * Whether [whereMatchesQuery] should search descriptions
		 * @since 2.0.0
		 */
		var querySearchDescription: Boolean = true
	): StandardFilters("tags", "tags") {
		override fun applyTo(query: ConditionProvider) {
			applyStandardFiltersTo(query)

			val prefix = "$table.$colPrefix"

			if(whereCreatorInternalIdIs is Some)
				query.addConditions(field("${prefix}_creator").eq((whereCreatorInternalIdIs as Some).value))
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
						if(querySearchDescription)
							add("${prefix}_description")
					}
				)
			}
		}

		override fun setWithRequest(req: HttpServerRequest) {
			setStandardFiltersWithRequest(req)

			val params = req.params()

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
	 * Values to update on tag rows
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
		var description: Option<String> = none()
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "tags.tag_") {
				if(fieldVal is Some)
					query.addValue(field(prefix + name), if(fieldVal.value is JsonObject) fieldVal.value.encode() else fieldVal.value)
			}

			set("name", name)
			set("description", description)
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
			SortOrder.CREATED_TS -> orderBy("tags.tag_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("tags.tag_modified_ts")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("tags.tag_name")
			SortOrder.FILE_COUNT -> orderBy("tags.tag_file_count")
		}

		return this
	}

	/**
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 * @param type The context filter type
	 */
	private fun applyContextFilters(query: ConditionProvider, type: ContextFilterType) {
		applyGenericPermissionCreatorContextFilter(query, type, "tags", "tags.tag_creator", context?.account?.excludeOtherTags)
	}

	/**
	 * Generates a query for getting DTO info
	 * @return The query
	 */
	private fun infoQuery(): SelectQuery<*> {
		val select = Sql.select(
			field("tags.id"),
			field("tag_id"),
			field("tag_name"),
			field("tag_description"),
			field("account_id").`as`("tag_creator_id"),
			field("account_name").`as`("tag_creator_name"),
			field("tag_file_count"),
			field("tag_created_ts"),
			field("tag_modified_ts")
		)

		return select
			.from(table("tags"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq(field("tags.tag_creator")))
			.query
	}

	/**
	 * Creates a new tag row with the provided details
	 * @param name The name
	 * @param description The description
	 * @param creatorInternalId The tag creator's internal ID
	 * @return The newly created tag row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		name: String,
		description: String,
		creatorInternalId: Int
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("tags"),
			field("tag_id"),
			field("tag_name"),
			field("tag_description"),
			field("tag_creator")
		)
			.values(
				id,
				name,
				description,
				creatorInternalId
			)
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Creates a tag use row
	 * @param tagInternalId The tag's internal ID
	 * @param fileInternalId The file's internal ID
	 * @since 2.0.0
	 */
	suspend fun createTagUseRow(tagInternalId: Int, fileInternalId: Int) {
		Sql.insertInto(
			table("tag_uses"),
			field("use_tag", tagInternalId),
			field("use_file", fileInternalId)
		).executeAwait()
	}

	/**
	 * Fetches many tags' info DTOs.
	 * Use [fetchOneDto] to fetch only one tag.
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
	): List<TagDto> {
		val query = infoQuery()

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { TagDto.fromRow(it) }
	}

	/**
	 * Fetches many tags' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one tag.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: TagPagination<TColType>,
		limit: Int,
		filters: Filters = Filters()
	): RowPagination.Results<TagDto, SortOrder, TColType> {
		val query = infoQuery()

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { TagDto.fromRow(it) }
	}

	/**
	 * Fetches one file tag's info DTO.
	 * Use [fetchManyDtos] to fetch multiple tags.
	 * @param filters Additional filters to apply
	 * @return The file tag DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(filters: Filters = Filters()): TagDto? {
		val query = infoQuery()

		applyContextFilters(query, ContextFilterType.VIEW)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			TagDto.fromRow(row)
	}

	/**
	 * Fetches many tag rows.
	 * Use [fetchOneRow] to fetch only one tag.
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
	): List<TagRow> {
		val query =
			Sql.select(asterisk())
				.from(table("tags"))
				.query

		applyContextFilters(query, ContextFilterType.LIST)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { TagRow.fromRow(it) }
	}

	/**
	 * Fetches one tag row.
	 * Use [fetchManyRows] to fetch many tags.
	 * @param filters Additional filters to apply
	 * @return The tag row, or null if there was no result
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): TagRow? {
		val query =
			Sql.select(asterisk())
				.from(table("tags"))
				.query

		applyContextFilters(query, ContextFilterType.VIEW)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			TagRow.fromRow(row)
	}

	/**
	 * Updates many tag rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the tags' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("tags"))

		applyContextFilters(query, ContextFilterType.UPDATE)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("tags.tag_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates a tag row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the tag's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many tag rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("tags"))

		applyContextFilters(query, ContextFilterType.DELETE)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one tag row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}