@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.*
import net.termer.twinemedia.model.pagination.FilePagination
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
 * Database model for files
 * @since 1.2.0
 */
class FilesModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [FilesModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = FilesModel(null, true)
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
		 * File title alphabetically
		 * @since 2.0.0
		 */
		TITLE_ALPHABETICALLY,

		/**
		 * File name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY,

		/**
		 * File size
		 * @since 2.0.0
		 */
		FILE_SIZE,

		/**
		 * The number of tags the file has
		 * @since 2.0.0
		 */
		TAG_COUNT,

		/**
		 * The number of child files the file has
		 * @since 2.0.0
		 */
		CHILD_COUNT
	}

	/**
	 * Filters for fetching files
	 * @since 2.0.0
	 */
	class Filters(
		/**
		 * Matches files where the sequential internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereInternalIdIs: Option<Int> = none(),

		/**
		 * Matches files where the alphanumeric ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereIdIs: Option<String> = none(),

		/**
		 * Matches files where the creator's internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereCreatorInternalIdIs: Option<Int> = none(),

		/**
		 * Matches files where the MIME type matches this SQL LIKE pattern.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereMimeIsLike: Option<String> = none(),

		/**
		 * Matches files created before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches files created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereCreatedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches files modified before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedBefore: Option<OffsetDateTime> = none(),

		/**
		 * Matches files created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereModifiedAfter: Option<OffsetDateTime> = none(),

		/**
		 * Matches files where their values match this plaintext query.
		 * Search fields can be enabled by setting querySearch* properties to true.
		 *
		 * @since 2.0.0
		 */
		var whereMatchesQuery: Option<String> = none(),

		/**
		 * Whether [whereMatchesQuery] should search file titles
		 * @since 2.0.0
		 */
		var querySearchTitle: Boolean = true,

		/**
		 * Whether [whereMatchesQuery] should search filenames
		 * @since 2.0.0
		 */
		var querySearchName: Boolean = true,

		/**
		 * Whether [whereMatchesQuery] should search descriptions
		 * @since 2.0.0
		 */
		var querySearchDescription: Boolean = true
	): Model.Filters {
		override fun applyTo(query: ConditionProvider) {
			if(whereInternalIdIs is Some)
				query.addConditions(field("files.id").eq((whereInternalIdIs as Some).value))
			if(whereIdIs is Some)
				query.addConditions(field("files.file_id").eq((whereIdIs as Some).value))
			if(whereCreatorInternalIdIs is Some)
				query.addConditions(field("files.file_creator").eq((whereCreatorInternalIdIs as Some).value))
			if(whereMimeIsLike is Some)
				query.addConditions(field("files.file_mime").like((whereMimeIsLike as Some).value))
			if(whereCreatedBefore is Some)
				query.addConditions(field("files.file_created_ts").lt((whereCreatedBefore as Some).value))
			if(whereCreatedAfter is Some)
				query.addConditions(field("files.file_created_ts").gt((whereCreatedAfter as Some).value))
			if(whereModifiedBefore is Some)
				query.addConditions(field("files.file_modified_ts").lt((whereModifiedBefore as Some).value))
			if(whereModifiedAfter is Some)
				query.addConditions(field("files.file_modified_ts").gt((whereModifiedAfter as Some).value))
			if(whereMatchesQuery is Some) {
				query.addFulltextSearchCondition(
					(whereMatchesQuery as Some).value,
					ArrayList<String>().apply {
						if(querySearchTitle)
							add("files.file_title")
						if(querySearchName)
							add("files.file_name")
						if(querySearchDescription)
							add("files.file_description")
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
				querySearchTitle = params["querySearchTitle"] == "true"
				querySearchName = params["querySearchName"] == "true"
				querySearchDescription = params["querySearchDescription"] == "true"
			}
		}

	}

	/**
	 * Values to update on file rows
	 * @since 2.0.0
	 */
	class UpdateValues(
		/**
		 * Title
		 * @since 2.0.0
		 */
		var title: Option<String> = none(),

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
		 * The thumbnail key, or null for no thumbnail
		 * @since 2.0.0
		 */
		var thumbnailKey: Option<String?> = none(),

		/**
		 * Whether the file is currently processing
		 * @since 2.0.0
		 */
		var isProcessing: Option<Boolean> = none(),

		/**
		 * An error that occurred while processing the file
		 * @since 2.0.0
		 */
		var processError: Option<String?> = none()
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "files.file_") {
				if(fieldVal is Some)
					query.addValue(field(prefix + name), if(fieldVal.value is Array<*>) array(*fieldVal.value) else fieldVal.value)
			}

			set("title", title)
			set("name", name)
			set("description", description)
			set("thumbnail_key", thumbnailKey)
			set("processing", isProcessing)
			set("process_error", processError)
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
			SortOrder.CREATED_TS -> orderBy("files.file_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("files.file_modified_ts")
			SortOrder.TITLE_ALPHABETICALLY -> orderBy("files.file_title")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("files.file_name")
			SortOrder.FILE_SIZE -> orderBy("files.file_size")
			SortOrder.TAG_COUNT -> orderBy("files.file_tag_count")
			SortOrder.CHILD_COUNT -> orderBy("files.file_child_count")
		}

		return this
	}

	/**
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 * @param isListing Whether this is a listing query, as opposed to a single-row viewing query or an update/delete
	 */
	private fun applyContextFilters(query: ConditionProvider, isListing: Boolean) {
		if(!ignoreContext) {
			if(context == null) {
				// If there is no context, do not show any files
				query.addConditions(falseCondition())
			} else {
				val acc = context!!.account
				val perm = if(isListing) "files.list.all" else "files.view.all"

				if(!acc.hasPermission(perm) || acc.excludeOtherLists)
					query.addConditions(field("files.file_creator").eq(acc.internalId))
			}
		}
	}

	/**
	 * Generates a query for getting DTO info
	 * @param includeDescription Whether to include file descriptions
	 * @param includeMeta Whether to include file metadata
	 * @return The query
	 */
	private fun infoQuery(includeDescription: Boolean, includeMeta: Boolean): SelectQuery<*> {
		val select = Sql.select(
			field("files.id"),
			field("file_title"),
			field("file_name"),
			field("file_size"),
			field("file_mime"),
			field("file_hash"),
			field(field("file_thumbnail").isNotNull).`as`("file_has_thumbnail"),
			field("file_tag_count"),
			field("file_child_count"),
			field("file_processing"),
			field("file_process_error"),
			field("account_id").`as`("file_creator_id"),
			field("account_name").`as`("file_creator_name"),
			field("source_id").`as`("file_source_id"),
			field("source_name").`as`("file_source_name"),
			field("source_type").`as`("file_source_type"),
			field("file_created_ts"),
			field("file_modified_ts")
		)

		if(includeDescription)
			select.select(field("file_description"))
		if(includeMeta)
			select.select(field("file_meta"))

		return select
			.from(table("files"))
			.leftJoin(table("accounts")).on(field("accounts.id").eq("files.file_creator"))
			.join(table("sources")).on(field("sources.id").eq("files.file_source"))
			.query
	}

	/**
	 * Creates a new file row with the provided details
	 * @param title The title
	 * @param name The filename
	 * @param description The description (can be blank)
	 * @param size The file size, in bytes
	 * @param mime The file MIME type
	 * @param key The file source key for the file
	 * @param hash The file hash (hex-encoded)
	 * @param thumbnailKey The file thumbnail's key (or null if it has no thumbnail)
	 * @param meta Additional metadata for the file (such as video/audio bitrate, resolution, etc.) in JSON format (can be an empty [JsonObject])
	 * @param parentInternalId The internal ID of the file's parent, or null if it is not a child
	 * @param isProcessing Whether the file is currently processing
	 * @param sourceInternalId The internal ID of the file source where the file resides
	 * @param creatorInternalId The file creator's internal ID
	 * @return The newly created file row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		title: String,
		name: String,
		description: String,
		size: Long,
		mime: String,
		key: String,
		hash: String,
		thumbnailKey: String?,
		meta: JsonObject,
		parentInternalId: Int?,
		isProcessing: Boolean,
		sourceInternalId: Int,
		creatorInternalId: Int
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("files"),
			field("file_id"),
			field("file_title"),
			field("file_name"),
			field("file_description"),
			field("file_size"),
			field("file_mime"),
			field("file_key"),
			field("file_hash"),
			field("file_thumbnail_key"),
			field("file_meta"),
			field("file_parent"),
			field("file_processing"),
			field("file_source"),
			field("file_creator")
		)
			.values(
				id,
				title,
				name,
				description,
				size,
				mime,
				key,
				hash,
				thumbnailKey,
				meta,
				parentInternalId,
				isProcessing,
				sourceInternalId,
				creatorInternalId
			)
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Fetches many files' info DTOs.
	 * Use [fetchOneDto] to fetch only one file.
	 * @param filters Additional filters to apply
	 * @param order Which order to sort results with (defaults to [SortOrder.CREATED_TS])
	 * @param orderDesc Whether to sort results in descending order (defaults to false)
	 * @param limit The number of results to return (defaults to [API_MAX_RESULT_LIMIT])
	 * @param includeDescription Whether to include file descriptions
	 * @param includeMeta Whether to include file metadata
	 * @return The results
	 */
	suspend fun fetchManyDtos(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT,
		includeDescription: Boolean,
		includeMeta: Boolean
	): List<FileDto> {
		val query = infoQuery(includeDescription, includeMeta)

		applyContextFilters(query, isListing = true)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { FileDto.fromRow(it) }
	}

	/**
	 * Fetches many files' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one file.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @param includeDescription Whether to include file descriptions
	 * @param includeMeta Whether to include file metadata
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: FilePagination<TColType>,
		limit: Int,
		filters: Filters = Filters(),
		includeDescription: Boolean,
		includeMeta: Boolean
	): RowPagination.Results<FileDto, SortOrder, TColType> {
		val query = infoQuery(includeDescription, includeMeta)

		applyContextFilters(query, isListing = true)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { FileDto.fromRow(it) }
	}

	/**
	 * Fetches one file's info DTO.
	 * Use [fetchManyDtos] to fetch multiple files.
	 * @param filters Additional filters to apply
	 * @param includeDescription Whether to include file descriptions
	 * @param includeMeta Whether to include file metadata
	 * @return The file DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(
		filters: Filters = Filters(),
		includeDescription: Boolean,
		includeMeta: Boolean
	): FileDto? {
		val query = infoQuery(includeDescription, includeMeta)

		applyContextFilters(query, isListing = false)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			FileDto.fromRow(row)
	}

	/**
	 * Fetches many file rows.
	 * Use [fetchOneRow] to fetch only one file.
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
	): List<FileRow> {
		val query =
			Sql.select(asterisk())
				.from(table("files"))
				.query

		applyContextFilters(query, isListing = true)
		filters.applyTo(query)
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { FileRow.fromRow(it) }
	}

	/**
	 * Fetches one file row.
	 * Use [fetchManyRows] to fetch many files.
	 * @param filters Additional filters to apply
	 * @return The file row, or null if there was no result
	 */
	suspend fun fetchOneRow(filters: Filters = Filters()): FileRow? {
		val query =
			Sql.select(field("files.*"))
				.from(table("files"))
				.query

		applyContextFilters(query, isListing = false)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			FileRow.fromRow(row)
	}

	/**
	 * Updates many file rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the files' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("files"))

		applyContextFilters(query, isListing = false)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("files.file_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates a file row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the file's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many file rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("files"))

		applyContextFilters(query, isListing = false)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one file row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}