package net.termer.twinemedia.model

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.db.dataobject.AccountInfo
import net.termer.twinemedia.db.dataobject.Account
import net.termer.twinemedia.util.toJsonArray

/**
 * Database model for accounts
 * @since 1.2.0
 */
class AccountsModel {
	/**
	 * Creates a new AccountsModel
	 * @since 1.2.0
	 */
	constructor()

	/**
	 * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
	 * Order key:
	 * 0 = Creation date, newest to oldest
	 * 1 = Creation date, oldest to newest
	 * 2 = Name alphabetically, ascending
	 * 3 = Name alphabetically, descending
	 * 4 = Email alphabetically, ascending
	 * 5 = Email alphabetically, descending
	 * @param order The order
	 * @return The appropriate "ORDER BY" SQL for the selected order
	 * @since 1.0
	 */
	private fun orderBy(order: Int): String {
		return "ORDER BY " + when(order) {
			1 -> "account_creation_date ASC"
			2 -> "account_name ASC"
			3 -> "account_name DESC"
			4 -> "account_email ASC"
			5 -> "account_email DESC"
			else -> "account_creation_date DESC"
		}
	}

	/**
	 * SELECT statement for getting info
	 * @param extra Extra rows to select (can be null for none)
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect(extra: String?): String {
		return """
			SELECT
				accounts.id,
				account_email AS email,
				account_name AS name,
				account_admin AS admin,
				account_permissions AS permissions,
				account_default_source AS default_source,
				source_name AS default_source_name,
				source_type AS default_source_type,
				account_creation_date AS creation_date,
				(
					SELECT COUNT(*) FROM media
					WHERE media_creator = accounts.id
				) AS files_created
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM accounts
			LEFT JOIN sources ON sources.id = account_default_source
		""".trimIndent()
	}
	/**
	 * SELECT statement for getting info
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect() = infoSelect(null)

	/**
	 * Creates a new account entry with the provided details
	 * @param email The email address of the new account
	 * @param name The name of the new account
	 * @param admin Whether the new account will be an administrator
	 * @param permissions An array of permissions that the new account will have
	 * @param hash The password hash for the new account
	 * @param defaultSource The default media source for the new account
	 * @return The newly created account entry's ID
	 * @since 1.5.0
	 */
	suspend fun createAccountEntry(email: String, name: String, admin: Boolean, permissions: Array<String>, hash: String, defaultSource: Int): Int {
		return SqlTemplate
				.forQuery(client, """
					INSERT INTO accounts
					( account_email, account_name, account_admin, account_permissions, account_hash, account_default_source )
					VALUES
					( #{email}, #{name}, #{admin}, CAST( #{perms} AS jsonb ), #{hash}, #{defaultSource} )
					RETURNING id
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"email" to email,
						"name" to name,
						"admin" to admin,
						"perms" to permissions.toJsonArray(),
						"hash" to hash,
						"defaultSource" to defaultSource
				)).await()
				.first().getInteger("id")
	}

	/**
	 * Fetches all account info
	 * @param offset The offset of the accounts to fetch
	 * @param limit The amount of accounts to return
	 * @param order The order to return the accounts
	 * @return All account info in the specified range
	 * @since 1.4.0
	 */
	suspend fun fetchAccountList(offset: Int, limit: Int, order: Int): RowSet<AccountInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(AccountInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches all accounts with names matching the specified plaintext query
	 * @param query The query to search for
	 * @param offset The offset of the accounts to fetch
	 * @param limit The amount of accounts to return
	 * @param order The order to return the accounts
	 * @return All accounts with names matching the specified plaintext query
	 * @since 1.5.0
	 */
	suspend fun fetchAccountsByPlaintextQuery(query: String, offset: Int, limit: Int, order: Int): RowSet<AccountInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE (to_tsvector(account_name) @@ plainto_tsquery(#{queryRaw}) OR LOWER(account_name) LIKE LOWER(#{query}))
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(AccountInfo.MAPPER)
				.execute(hashMapOf<String, Any?>(
						"queryRaw" to query,
						"query" to "%$query%",
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches all info about an account with the specified email (case-insensitive)
	 * @param email The email to search
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchAccountByEmail(email: String): RowSet<Account> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM accounts WHERE LOWER(account_email) = LOWER(#{email})
				""".trimIndent())
				.mapTo(Account.MAPPER)
				.execute(hashMapOf<String, Any>(
						"email" to email
				)).await()
	}

	/**
	 * Fetches all info about the account with the specified ID
	 * @param id The account's ID
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchAccountById(id: Int): RowSet<Account> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM accounts WHERE accounts.id = #{id}
				""".trimIndent())
				.mapTo(Account.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches all info about the account associated with the specified API key ID
	 * @param keyId The key ID
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchAccountAndApiKeyByKeyId(keyId: String): RowSet<Account> {
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
				.mapTo(Account.MAPPER)
				.execute(hashMapOf<String, Any>(
						"keyId" to keyId
				)).await()
	}

	/**
	 * Fetches all general account info about the account with the specified ID
	 * @param id The account's ID
	 * @return All rows from database search
	 * @since 1.4.0
	 */
	suspend fun fetchAccountInfoById(id: Int): RowSet<AccountInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE accounts.id = #{id}
				""".trimIndent())
				.mapTo(AccountInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches all admin accounts
	 * @return All accounts that are administrators
	 * @since 1.4.0
	 */
	suspend fun fetchAdminAccounts(): RowSet<Account> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM accounts WHERE account_admin = true
				""".trimIndent())
				.mapTo(Account.MAPPER)
				.execute(hashMapOf()).await()
	}

	/**
	 * Fetches the amount of accounts that do not have a default media source associated with them
	 * @return The amount of accounts that do not have a default media source associated with them
	 * @since 1.5.0
	 */
	suspend fun fetchAccountsWithoutSourceCount(): Int {
		return SqlTemplate
				.forQuery(client, """
					SELECT COUNT(*) FROM accounts
					WHERE account_default_source = -1
				""".trimIndent())
				.execute(mapOf()).await()
				.first().getInteger("count")
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
	 * @since 1.0
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
	 * @since 1.0
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