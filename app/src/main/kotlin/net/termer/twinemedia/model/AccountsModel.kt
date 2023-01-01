package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.AccountDto
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.dataobject.RowIdPair
import net.termer.twinemedia.model.pagination.AccountPagination
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
 * Database model for accounts
 * @since 1.2.0
 */
class AccountsModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [AccountsModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = AccountsModel(null, true)
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
		 * Account name alphabetically
		 * @since 2.0.0
		 */
		NAME_ALPHABETICALLY,

		/**
		 * Account email alphabetically
		 * @since 2.0.0
		 */
		EMAIL_ALPHABETICALLY,

		/**
		 * The number of files the account has created
		 * @since 2.0.0
		 */
		FILE_COUNT
	}

	/**
	 * Filters for fetching accounts
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
		 * Matches rows where the email is this (case-insensitive).
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereEmailIs: Option<String> = none(),

		/**
		 * Matches rows where the ID of the API key associated with it is this.
		 * Will be set to null if used in a DTO fetch method or if API key fetching is disabled on the row fetch method.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		var whereApiKeyIdIs: Option<String> = none(),

		/**
		 * Matches rows that have this administrator status.
		 * API-safe.
		 * @since 2.0.0
		 */
		var whereAdminStatusIs: Option<Boolean> = none(),

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
		 * Whether [whereMatchesQuery] should search emails
		 * @since 2.0.0
		 */
		var querySearchEmail: Boolean = true
	): StandardFilters("accounts", "account") {
		override fun genConditions(): MutableList<Condition> {
			val res = genStandardConditions()

			val prefix = "$table.$colPrefix"

			if(whereEmailIs is Some)
				res.add(field("${prefix}_email").equalIgnoreCase((whereEmailIs as Some).value))
			if(whereApiKeyIdIs is Some)
				res.add(field("api_keys.key_id").eq((whereApiKeyIdIs as Some).value))
			if(whereAdminStatusIs is Some)
				res.add(field("${prefix}_admin").eq((whereAdminStatusIs as Some).value))
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
						if(querySearchEmail)
							add("${prefix}_email")
					}
				))
			}

			return res
		}

		override fun setWithRequest(req: HttpServerRequest) {
			setStandardFiltersWithRequest(req)

			val params = req.params()

			if(params.contains("whereEmailIs"))
				whereEmailIs = some(params["whereEmailIs"])
			if(params.contains("whereAdminStatusIs"))
				whereAdminStatusIs = some(params["whereAdminStatusIs"] == "true")
			if(params.contains("whereFileCountLessThan"))
				whereFileCountLessThan = some(params["whereFileCountLessThan"].toIntOr(Int.MAX_VALUE))
			if(params.contains("whereFileCountMoreThan"))
				whereFileCountMoreThan = some(params["whereFileCountMoreThan"].toIntOr(0))
			if(params.contains("whereCreatedBefore"))
				whereCreatedBefore = dateStringToOffsetDateTimeOrNone(params["whereCreatedBefore"])
			if(params.contains("whereMatchesQuery")) {
				whereMatchesQuery = some(params["whereMatchesQuery"])
				querySearchName = params["querySearchName"] == "true"
				querySearchEmail = params["querySearchEmail"] == "true"
			}
		}
	}

	/**
	 * Values to update on account rows
	 * @since 2.0.0
	 */
	class UpdateValues(
		/**
		 * Name
		 * @since 2.0.0
		 */
		var name: Option<String> = none(),

		/**
		 * Email address
		 * @since 2.0.0
		 */
		var email: Option<String> = none(),

		/**
		 * Permissions
		 * @since 2.0.0
		 */
		var permissions: Option<Array<String>> = none(),

		/**
		 * Whether the account is an administrator
		 * @since 2.0.0
		 */
		var isAdmin: Option<Boolean> = none(),

		/**
		 * Password hash
		 * @since 2.0.0
		 */
		var hash: Option<String> = none(),

		/**
		 * The tags to exclude globally when listing and searching files
		 * @since 2.0.0
		 */
		var excludeTags: Option<Array<String>> = none(),

		/**
		 * Whether to globally exclude files created by other accounts
		 * @since 2.0.0
		 */
		var excludeOtherFiles: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude lists created by other accounts
		 * @since 2.0.0
		 */
		var excludeOtherLists: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude tags on files created by other accounts
		 * @since 2.0.0
		 */
		var excludeOtherTags: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude process presets created by other accounts
		 * @since 2.0.0
		 */
		var excludeOtherProcessPresets: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude file sources created by other accounts
		 * @since 2.0.0
		 */
		var excludeOtherSources: Option<Boolean> = none(),

		/**
		 * Default source internal ID
		 * @since 2.0.0
		 */
		var defaultSourceId: Option<Int> = none()
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "account_") {
				if(fieldVal is Some)
					query.addValue(field(prefix + name), if(fieldVal.value is Array<*>) array(*fieldVal.value) else fieldVal.value)
			}

			set("name", name)
			set("email", email)
			set("permissions", permissions)
			set("admin", isAdmin)
			set("hash", hash)
			set("exclude_tags", excludeTags)
			set("exclude_other_files", excludeOtherFiles)
			set("exclude_other_lists", excludeOtherLists)
			set("exclude_other_tags", excludeOtherTags)
			set("exclude_other_process_presets", excludeOtherProcessPresets)
			set("exclude_other_sources", excludeOtherSources)
			set("default_source", defaultSourceId)
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
			SortOrder.CREATED_TS -> orderBy("accounts.account_created_ts")
			SortOrder.MODIFIED_TS -> orderBy("accounts.account_modified_ts")
			SortOrder.NAME_ALPHABETICALLY -> orderBy("accounts.account_name")
			SortOrder.EMAIL_ALPHABETICALLY -> orderBy("accounts.account_email")
			SortOrder.FILE_COUNT -> orderBy("accounts.account_file_count")
		}

		return this
	}

	/**
	 * Generates context filter conditions
	 * @param type The context filter type
	 * @return The conditions
	 */
	private fun genContextFilterConditions(type: ContextFilterType): MutableList<Condition> {
		// No filters to apply
		// Higher level permission checks on controllers restrict access to account data

		return ArrayList(0)
	}

	/**
	 * Generates a query for getting DTO info
	 * @return The query
	 */
	private fun infoQuery() =
		Sql.select(
			field("accounts.id"),
			field("account_email"),
			field("account_name"),
			field("account_admin"),
			field("account_permissions"),
			field("source_id").`as`("account_default_source_id"),
			field("source_name").`as`("account_default_source_name"),
			field("source_type").`as`("account_default_source_type"),
			field("account_file_count"),
			field("account_created_ts"),
			field("account_modified_ts")
		)
			.from(table("accounts"))
			.leftJoin(table("sources")).on(field("sources.id").eq(field("account_default_source")))
			.query

	/**
	 * Creates a new account row with the provided details
	 * @param email The email address of the new account
	 * @param name The name of the new account
	 * @param isAdmin Whether the new account will be an administrator
	 * @param permissions An array of permissions that the new account will have
	 * @param hash The password hash for the new account
	 * @param defaultSourceInternalId The new account's default media source ID, or null for none
	 * @return The newly created account row's ID
	 * @since 2.0.0
	 */
	suspend fun createRow(
		email: String,
		name: String,
		isAdmin: Boolean,
		permissions: Array<String>,
		hash: String,
		defaultSourceInternalId: Int?
	): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("accounts"),
			field("account_id"),
			field("account_name"),
			field("account_email"),
			field("account_admin"),
			field("account_permissions"),
			field("account_hash"),
			field("account_default_source")
		)
			.values(id, name, email, isAdmin, permissions, hash, defaultSourceInternalId)
			.returning(field("id"))
			.fetchOneAwait()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Fetches many accounts' info DTOs.
	 * Use [fetchOneDto] to fetch only one account.
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
	): List<AccountDto> {
		val query = infoQuery()

		filters.whereApiKeyIdIs = none()

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { AccountDto.fromRow(it) }
	}

	/**
	 * Fetches many accounts' info DTOs using pagination.
	 * Use [fetchOneDto] to fetch only one account.
	 * @param pagination The pagination data to use
	 * @param filters Additional filters to apply
	 * @param limit The number of results to return
	 * @return The paginated results
	 * @since 2.0.0
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: AccountPagination<TColType>,
		limit: Int,
		filters: Filters = Filters()
	): RowPagination.Results<AccountDto, SortOrder, TColType> {
		val query = infoQuery()

		filters.whereApiKeyIdIs = none()

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())

		return query.fetchPaginatedAsync(pagination, limit) { AccountDto.fromRow(it) }
	}

	/**
	 * Fetches one account's info DTO.
	 * Use [fetchManyDtos] to fetch multiple accounts.
	 * @param filters Additional filters to apply
	 * @return The account DTO, or null if there was no result
	 * @since 2.0.0
	 */
	suspend fun fetchOneDto(filters: Filters = Filters()): AccountDto? {
		val query = infoQuery()

		filters.whereApiKeyIdIs = none()

		query.addConditions(genContextFilterConditions(ContextFilterType.VIEW))
		query.addConditions(filters.genConditions())
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			AccountDto.fromRow(row)
	}

	private fun handleFetchKeyInfo(query: SelectQuery<*>, filters: Filters, fetchApiKeyInfo: Boolean) {
		if(fetchApiKeyInfo) {
			query.addSelect(field("api_keys.key_permissions"))
			query.addJoin(
				table("api_keys"),
				field("api_keys.key_owner").eq(field("accounts.id"))
			)
		} else {
			filters.whereApiKeyIdIs = none()
		}
	}

	/**
	 * Fetches many account rows.
	 * Use [fetchOneRow] to fetch only one account.
	 * @param filters Additional filters to apply
	 * @param order Which order to sort results with (defaults to [SortOrder.CREATED_TS])
	 * @param orderDesc Whether to sort results in descending order (defaults to false)
	 * @param limit The number of results to return (defaults to [API_MAX_RESULT_LIMIT])
	 * @param fetchApiKeyInfo Whether to fetch key info associated with the account (should be used in conjunction with [Filters.whereApiKeyIdIs]) (defaults to false)
	 * @return The results
	 * @since 2.0.0
	 */
	suspend fun fetchManyRows(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT,
		fetchApiKeyInfo: Boolean = false
	): List<AccountRow> {
		val query =
			Sql.select(asterisk())
				.from(table("accounts"))
				.query

		handleFetchKeyInfo(query, filters, fetchApiKeyInfo)

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())
		query.orderBy(order, orderDesc)
		query.addLimit(limit)

		return query.fetchManyAwait().map { AccountRow.fromRow(it) }
	}

	/**
	 * Fetches one account row.
	 * Use [fetchManyRows] to fetch many accounts.
	 * @param filters Additional filters to apply
	 * @param fetchApiKeyInfo Whether to fetch key info associated with the account (should be used in conjunction with [Filters.whereApiKeyIdIs]) (defaults to false)
	 * @return The account row, or null if there was no result
	 * @since 2.0.0
	 */
	suspend fun fetchOneRow(filters: Filters = Filters(), fetchApiKeyInfo: Boolean = false): AccountRow? {
		val query =
			Sql.select(field("accounts.*"))
				.from(table("accounts"))
				.query

		handleFetchKeyInfo(query, filters, fetchApiKeyInfo)

		query.addConditions(genContextFilterConditions(ContextFilterType.VIEW))
		query.addConditions(filters.genConditions())
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			AccountRow.fromRow(row)
	}

	/**
	 * Counts rows, taking into account any filters
	 * @param filters Additional filters to apply
	 * @return The row count
	 * @since 2.0.0
	 */
	suspend fun count(filters: Filters = Filters()): Int {
		val query =
			Sql.selectCount()
				.from("accounts")
				.query

		query.addConditions(genContextFilterConditions(ContextFilterType.LIST))
		query.addConditions(filters.genConditions())

		return query.fetchOneAwait()!!.getInteger("count")
	}

	/**
	 * Updates many account rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the accounts' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateMany(values: UpdateValues, filters: Filters, limit: Int? = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("accounts"))

		query.addConditions(genContextFilterConditions(ContextFilterType.UPDATE))
		query.addConditions(filters.genConditions())
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("account_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates an account row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the account's last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOne(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateMany(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many account rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteMany(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("accounts"))

		query.addConditions(genContextFilterConditions(ContextFilterType.DELETE))
		query.addConditions(filters.genConditions())
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one account row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOne(filters: Filters) {
		deleteMany(filters, 1)
	}
}

