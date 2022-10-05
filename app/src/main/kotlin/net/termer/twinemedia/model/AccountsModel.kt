@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.Constants.API_MAX_RESULT_LIMIT
import net.termer.twinemedia.dataobject.AccountDto
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.dataobject.RowIdPair
import net.termer.twinemedia.model.pagination.AccountPagination
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.Option
import net.termer.twinemedia.util.Some
import net.termer.twinemedia.util.db.*
import net.termer.twinemedia.util.none
import org.jooq.ConditionProvider
import org.jooq.Query
import org.jooq.UpdateQuery
import net.termer.twinemedia.util.db.Database.Sql
import org.jooq.impl.DSL.*

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
		/**
		 * Matches accounts where the sequential internal ID is this
		 * @since 2.0.0
		 */
		var whereInternalIdIs: Int? = null,

		/**
		 * Matches accounts where the alphanumeric ID is this
		 * @since 2.0.0
		 */
		var whereIdIs: String? = null,

		/**
		 * Matches accounts where the email is this (case-insensitive)
		 * @since 2.0.0
		 */
		var whereEmailIs: String? = null,

		/**
		 * Matches accounts where the ID of the API key associated with it is this.
		 * Will be set to null if used in a DTO fetch method or if API key fetching is disabled on the row fetch method.
		 * @since 2.0.0
		 */
		var whereApiKeyIdIs: String? = null,

		/**
		 * Matches accounts where their values match this plaintext query.
		 * Currently matches the account name and email fields.
		 * @since 2.0.0
		 */
		var whereMatchesQuery: String? = null,

		/**
		 * Matches accounts that have this administrator status
		 * @since 2.0.0
		 */
		var whereIsAdmin: Boolean? = null,
	): Model.Filters {
		override fun applyTo(query: ConditionProvider) {
			if(whereInternalIdIs != null)
				query.addConditions(field("accounts.id").eq(whereInternalIdIs))
			if(whereIdIs != null)
				query.addConditions(field("accounts.account_id").eq(whereIdIs))
			if(whereEmailIs != null)
				query.addConditions(field("accounts.account_email").equalIgnoreCase(whereEmailIs))
			if(whereApiKeyIdIs != null)
				query.addConditions(field("api_keys.key_id").eq(whereApiKeyIdIs))
			//if(whereMatchesQuery)
			// TODO Probably needs to use raw SQL with binds for this
			if(whereIsAdmin != null)
				query.addConditions(field("accounts.account_admin").eq(whereIsAdmin))
		}

		override fun setWithRequest(req: HttpServerRequest) {
			TODO("Figure out which fields apply to this")
			// TODO This is the place to enforce maximum pagination limits for requests
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
		val excludeOtherFiles: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude lists created by other accounts
		 * @since 2.0.0
		 */
		val excludeOtherLists: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude tags on files created by other accounts
		 * @since 2.0.0
		 */
		val excludeOtherTags: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude process presets created by other accounts
		 * @since 2.0.0
		 */
		val excludeOtherProcessPresets: Option<Boolean> = none(),

		/**
		 * Whether to globally exclude file sources created by other accounts
		 * @since 2.0.0
		 */
		val excludeOtherSources: Option<Boolean> = none(),

		/**
		 * Default source internal ID
		 * @since 2.0.0
		 */
		var defaultSourceId: Option<Int> = none()
	): Model.UpdateValues {
		override fun applyTo(query: UpdateQuery<*>) {
			fun set(name: String, fieldVal: Option<*>, prefix: String = "accounts.account_") {
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
	 * Applies context filters on a query
	 * @param query The query to apply the filters on
	 */
	private fun applyContextFilters(query: ConditionProvider) {
		// No filters to apply
		// Higher level permission checks on controllers restrict access to account data
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
			.leftJoin(table("sources")).on(field("sources.id").eq("account_default_source"))
			.query

	/**
	 * Creates a new account row with the provided details
	 * @param email The email address of the new account
	 * @param name The name of the new account
	 * @param isAdmin Whether the new account will be an administrator
	 * @param permissions An array of permissions that the new account will have
	 * @param hash The password hash for the new account
	 * @param defaultSourceId The new account's default media source ID, or null for none
	 * @return The newly created account entry's ID
	 * @since 2.0.0
	 */
	suspend fun createAccountRow(email: String, name: String, isAdmin: Boolean, permissions: Array<String>, hash: String, defaultSourceId: Int?): RowIdPair {
		val id = genRowId()

		val internalId = Sql.insertInto(
			table("accounts"),
			field("account_id"),
			field("account_email"),
			field("account_admin"),
			field("account_permissions"),
			field("account_hash"),
			field("account_default_source")
		)
			.values(id, email, isAdmin, permissions, hash, defaultSourceId)
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
	 */
	suspend fun fetchManyDtos(
		filters: Filters = Filters(),
		order: SortOrder = SortOrder.CREATED_TS,
		orderDesc: Boolean = false,
		limit: Int = API_MAX_RESULT_LIMIT
	): List<AccountDto> {
		val query = infoQuery()

		filters.whereApiKeyIdIs = null

		applyContextFilters(query)
		filters.applyTo(query)
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
	 */
	suspend fun <TColType> fetchManyDtosPaginated(
		pagination: AccountPagination<TColType>,
		limit: Int,
		filters: Filters = Filters()
	): RowPagination.Results<AccountDto, SortOrder, TColType> {
		val query = infoQuery()

		filters.whereApiKeyIdIs = null

		applyContextFilters(query)
		filters.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { AccountDto.fromRow(it) }
	}

	/**
	 * Fetches one account's info DTO.
	 * Use [fetchManyDtos] to fetch multiple accounts.
	 * @param filters Additional filters to apply
	 * @return The account DTO, or null if there was no result
	 */
	suspend fun fetchOneDto(filters: Filters = Filters()): AccountDto? {
		val query = infoQuery()

		filters.whereApiKeyIdIs = null

		applyContextFilters(query)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			AccountDto.fromRow(row)
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

		if(fetchApiKeyInfo) {
			query.addSelect(field("api_keys.key_id"), field("api_keys.key_permissions"))
			query.addJoin(
				table("api_keys"),
				field("api_keys.key_owner").eq(field("accounts.id"))
			)
		} else {
			filters.whereApiKeyIdIs = null
		}

		applyContextFilters(query)
		filters.applyTo(query)
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
	 */
	suspend fun fetchOneRow(filters: Filters = Filters(), fetchApiKeyInfo: Boolean = false): AccountRow? {
		val query =
			Sql.select(field("accounts.*"))
				.from(table("accounts"))
				.query

		if(fetchApiKeyInfo) {
			query.addSelect(field("api_keys.key_id"), field("api_keys.key_permissions"))
			query.addJoin(
				table("api_keys"),
				field("api_keys.key_owner").eq(field("accounts.id"))
			)
		} else {
			filters.whereApiKeyIdIs = null
		}

		applyContextFilters(query)
		filters.applyTo(query)
		query.addLimit(1)

		val row = query.fetchOneAwait()

		return if(row == null)
			null
		else
			AccountRow.fromRow(row)
	}

	/**
	 * Updates many account rows
	 * @param values The values to update
	 * @param filters Filters for which rows to update
	 * @param limit The maximum number of rows to update, or null for no limit (defaults to null)
	 * @param updateModifiedTs Whether to update the accounts' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateManyAccounts(values: UpdateValues, filters: Filters, limit: Int?  = null, updateModifiedTs: Boolean = true) {
		val query = Sql.updateQuery(table("accounts"))

		applyContextFilters(query)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		values.applyTo(query)

		if(updateModifiedTs)
			query.addValue(field("accounts.account_modified_ts"), now())

		query.executeAwait()
	}

	/**
	 * Updates an account row
	 * @param values The values to update
	 * @param filters Filters for which row to update
	 * @param updateModifiedTs Whether to update the accounts' last modified timestamp (defaults to true)
	 * @since 2.0.0
	 */
	suspend fun updateOneAccount(values: UpdateValues, filters: Filters, updateModifiedTs: Boolean = true) {
		updateManyAccounts(values, filters, 1, updateModifiedTs)
	}

	/**
	 * Deletes many account rows
	 * @param filters Filters for which rows to delete
	 * @param limit The maximum number of rows to delete, or null for no limit (defaults to null)
	 * @since 2.0.0
	 */
	suspend fun deleteManyAccounts(filters: Filters, limit: Int? = null) {
		val query = Sql.deleteQuery(table("accounts"))

		applyContextFilters(query)
		filters.applyTo(query)
		if(limit != null)
			query.addLimit(limit)

		query.executeAwait()
	}

	/**
	 * Deletes one account row
	 * @param filters Filters for which row to delete
	 * @since 2.0.0
	 */
	suspend fun deleteOneAccount(filters: Filters) {
		deleteManyAccounts(filters, 1)
	}
}