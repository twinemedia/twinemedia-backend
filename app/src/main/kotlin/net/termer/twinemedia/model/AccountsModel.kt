package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import net.termer.twinemedia.dataobject.AccountDto
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.dataobject.RowIdPair
import net.termer.twinemedia.model.pagination.AccountPagination
import net.termer.twinemedia.model.pagination.RowPagination
import net.termer.twinemedia.util.db.fetchOneAsync
import net.termer.twinemedia.util.db.fetchPaginatedAsync
import net.termer.twinemedia.util.db.genRowId
import net.termer.twinemedia.util.toJsonArray
import org.jooq.SelectQuery
import org.jooq.impl.DSL.*;

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
	class FetchOptions(
		/**
		 * Fetch accounts where the sequential internal ID is this
		 * @since 2.0.0
		 */
		var whereInternalIdIs: Int? = null,
		/**
		 * Fetch accounts where the alphanumeric ID is this
		 * @since 2.0.0
		 */
		var whereIdIs: String? = null,

		/**
		 * Fetch accounts where the email is this (case-insensitive)
		 * @since 2.0.0
		 */
		var whereEmailIs: String? = null,

		/**
		 * Fetches accounts where the API key TODO finish describing this
		 * @since 2.0.0
		 */
		var whereApiKeyIdIs: String? = null,

		/**
		 * Fetches accounts which have metadata that match this plaintext query
		 * @since 2.0.0
		 */
		var whereMatchesQuery: String? = null,

		/**
		 * Fetches accounts that have this administrator status
		 * @since 2.0.0
		 */
		var whereIsAdmin: Boolean? = null,

		/**
		 * Whether to include API key permissions TODO finish describing this
		 * @since 2.0.0
		 */
		var includeApiKeyPermissions: Boolean = false
	): Model.FetchOptions {
		override fun applyTo(query: SelectQuery<*>) {
			if(whereInternalIdIs != null)
				query.addConditions(field("accounts.id").eq(whereInternalIdIs))
			if(whereIdIs != null)
				query.addConditions(field("accounts.account_id").eq(whereIdIs))
			if(whereEmailIs != null)
				query.addConditions(field("accounts.account_email").equalIgnoreCase(whereEmailIs))
			//if(includeApiKeyPermissions && whereApiKeyIdIs != null)
			// TODO Need to figure out what's going on
			//if(whereMatchesQuery)
			// TODO Probably needs to use raw SQL with binds for this
			if(whereIsAdmin != null)
				query.addConditions(field("accounts.account_admin").eq(whereIsAdmin))
		}

		override fun useRequest(req: HttpServerRequest) {
			TODO("Figure out which fields apply to this")
		}

	}

	/**
	 * Generates a query for getting info
	 * @return The query
	 */
	private fun infoQuery() =
		select(
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
		.from("accounts")
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
	 * @since 1.5.0
	 */
	suspend fun createAccountRow(email: String, name: String, isAdmin: Boolean, permissions: Array<String>, hash: String, defaultSourceId: Int?): RowIdPair {
		val id = genRowId()

		val internalId = insertInto(
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
			.fetchOneAsync()!!
			.getInteger("id")

		return RowIdPair(internalId, id)
	}

	/**
	 * Fetches many accounts' info.
	 * Use [fetchOneInfo] to fetch only one account.
	 * @param limit The number of results to return
	 * @param options Additional options to apply
	 * @return The paginated results
	 */
	suspend fun <TColType> fetchManyInfo(
		pagination: AccountPagination<TColType>,
		limit: Int,
		options: FetchOptions = FetchOptions()
	): RowPagination.Results<AccountDto, SortOrder, TColType> {
		val query = infoQuery()

		// TODO Apply context-based filters with a standardized method.
		// Even if there are currently no filters to be applied, add dummy function for later use and still use it.

		options.applyTo(query)

		return query.fetchPaginatedAsync(pagination, limit) { AccountDto.fromRow(it) }
	}

	/**
	 * Fetches one account's info.
	 * Use [fetchManyInfo] to fetch only multiple accounts.
	 * @param options Additional options to apply
	 * @return The account DTO, or null if there was no result
	 */
	suspend fun fetchOneInfo(options: FetchOptions = FetchOptions()): AccountDto? {
		val query = infoQuery()

		// TODO Apply context-based filters with a standardized method.
		// Even if there are currently no filters to be applied, add dummy function for later use and still use it.

		options.applyTo(query)

		val row = query.fetchOneAsync()

		return if(row == null)
			null
		else
			AccountDto.fromRow(row)
	}

	/**
	 * Fetches all info about the account associated with the specified API key ID
	 * @param keyId The key ID
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchAccountAndApiKeyByKeyId(keyId: String): RowSet<AccountRow> {
		// TODO Take logic from this and use it in FilterOptions.applyTo
		return SqlTemplate
			.forQuery(client, """
				SELECT
					accounts.*,
					key_permissions,
					key_id
				FROM accounts
				JOIN apikeys ON key_owner = accounts.id
				WHERE key_id = #{keyId}
			""".trimIndent())
			.mapTo(AccountRow.MAPPER)
			.execute(hashMapOf<String, Any>(
				"keyId" to keyId
			)).await()
	}

	/**
	 * Updates an account's info
	 * @param id The ID of the account
	 * @param newName The new name for this account
	 * @param newEmail The new email for this account
	 * @param isAdmin Whether this account will be an administrator
	 * @param newPermissions The new permissions for this account
	 * @param defaultSource The new default source's ID for this account
	 * @since 1.5.0
	 */
	suspend fun updateAccountInfo(id: Int, newName: String, newEmail: String, isAdmin: Boolean, newPermissions: Array<String>, defaultSource: Int) {
		SqlTemplate
			.forUpdate(client, """
				UPDATE accounts
				SET
					account_name = #{name},
					account_email = #{email},
					account_admin = #{admin},
					account_permissions = CAST( #{perms} AS jsonb ),
					account_default_source = #{defaultSource}
				WHERE accounts.id = #{id}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"id" to id,
				"name" to newName,
				"email" to newEmail,
				"admin" to isAdmin,
				"perms" to newPermissions.toJsonArray(),
				"defaultSource" to defaultSource
			)).await()
	}

	/**
	 * Updates an account's info
	 * @param id The ID of the account
	 * @param newName The new name for this account
	 * @param newEmail The new email for this account (null to leave unchanged)
	 * @param newHash The new password hash for this account (null to leave unchanged)
	 * @param newDefaultSource The new default media source ID for this account
	 * @param excludeTags Tags to globally exclude when listing files (from searches, lists, or anywhere else an array of files would be returned other than file children)
	 * @param excludeOtherMedia Whether to globally exclude media created by other users when viewing or listing any media
	 * @param excludeOtherLists Whether to globally exclude lists created by other users
	 * @param excludeOtherTags Whether to globally exclude tags added to files created by other users
	 * @param excludeOtherProcesses Whether to globally exclude processes created by other users
	 * @param excludeOtherSources Whether to globally exclude media sources created by other users
	 * @since 1.5.0
	 */
	suspend fun updateAccountInfo(id: Int, newName: String, newEmail: String?, newHash: String?, newDefaultSource: Int, excludeTags: Array<String>, excludeOtherMedia: Boolean, excludeOtherLists: Boolean, excludeOtherTags: Boolean, excludeOtherProcesses: Boolean, excludeOtherSources: Boolean) {
		SqlTemplate
			.forUpdate(client, """
				UPDATE accounts
				SET
					account_name = #{name},
					${if(newEmail == null) "" else "account_email = #{email},"}
					${if(newHash == null) "" else "account_hash = #{hash},"}
					account_default_source = #{defaultSource},
					account_exclude_tags = CAST( #{excludeTags} AS jsonb ),
					account_exclude_other_media = #{excludeOtherMedia},
					account_exclude_other_lists = #{excludeOtherLists},
					account_exclude_other_tags = #{excludeOtherTags},
					account_exclude_other_processes = #{excludeOtherProcesses},
					account_exclude_other_sources = #{excludeOtherSources}
				WHERE accounts.id = #{id}
			""".trimIndent())
			.execute(hashMapOf<String, Any?>(
				"id" to id,
				"name" to newName,
				"email" to newEmail,
				"hash" to newHash,
				"defaultSource" to newDefaultSource,
				"excludeTags" to excludeTags.toJsonArray(),
				"excludeOtherMedia" to excludeOtherMedia,
				"excludeOtherLists" to excludeOtherLists,
				"excludeOtherTags" to excludeOtherTags,
				"excludeOtherProcesses" to excludeOtherProcesses,
				"excludeOtherSources" to excludeOtherSources
			)).await()
	}

	/**
	 * Updates an account's password hash
	 * @param id The ID of the account
	 * @param newHash The new password hash for this account
	 * @since 1.0.0
	 */
	suspend fun updateAccountHash(id: Int, newHash: String) {
		SqlTemplate
			.forUpdate(client, """
				UPDATE accounts
				SET
					account_hash = #{hash}
				WHERE accounts.id = #{id}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"id" to id,
				"hash" to newHash
			)).await()
	}

	/**
	 * Updates the default source of all accounts with the specified default source
	 * @param oldSource The old source ID
	 * @param newSource The new source ID
	 * @since 1.5.0
	 */
	suspend fun updateAccountDefaultSourceByDefaultSource(oldSource: Int, newSource: Int) {
		SqlTemplate
			.forUpdate(client, """
				UPDATE accounts
				SET
					account_default_source = #{new}
				WHERE account_default_source = #{old}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"old" to oldSource,
				"new" to newSource
			)).await()
	}

	/**
	 * Deletes an account
	 * @param id The ID of the account to delete
	 * @since 1.0.0
	 */
	suspend fun deleteAccount(id: Int) {
		SqlTemplate
			.forUpdate(client, """
				DELETE FROM accounts
				WHERE accounts.id = #{id}
			""".trimIndent())
			.execute(hashMapOf<String, Any>(
				"id" to id
			)).await()
	}
}