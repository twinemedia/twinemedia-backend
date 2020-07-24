package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

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
        return "ORDER BY " + when (order) {
            1 -> "account_creation_date ASC"
            2 -> "account_name ASC"
            3 -> "account_name DESC"
            4 -> "account_email ASC"
            5 -> "account_email DESC"
            else -> "account_creation_date DESC"
        }
    }

    /**
     * Fetches all accounts
     * @param offset The offset of the accounts to fetch
     * @param limit The amount of accounts to return
     * @param order The order to return the accounts
     * @return All accounts in the specified range
     * @since 1.0
     */
    suspend fun fetchAccountList(offset: Int, limit: Int, order: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                id,
                account_email AS email,
                account_name AS name,
                account_admin AS admin,
                account_permissions AS permissions,
                account_creation_date AS creation_date
                FROM accounts
                ${orderBy(order)}
                OFFSET ? LIMIT ?
            """.trimIndent(),
                JsonArray()
                        .add(offset)
                        .add(limit)
        )
    }

    /**
     * Fetches all info about an account with the specified email
     * @param email The email to search
     * @return All rows from database search
     * @since 1.0
     */
    suspend fun fetchAccountByEmail(email: String): ResultSet? {
        return client?.queryWithParamsAwait(
                "SELECT * FROM accounts WHERE account_email = ?",
                JsonArray().add(email)
        )
    }

    /**
     * Fetches all info about the account with the specified ID
     * @param id The account's ID
     * @return All rows from database search
     * @since 1.0
     */
    suspend fun fetchAccountById(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                "SELECT * FROM accounts WHERE id = ?",
                JsonArray().add(id)
        )
    }

    /**
     * Fetches all info about the account associated with the specified API key ID
     * @param keyId The key ID
     * @return All rows from database search
     * @since 1.3.0
     */
    suspend fun fetchAccountAndApiKeyByKeyId(keyId: String): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    accounts.id,
                    account_email,
                    account_name,
                    account_admin,
                    account_permissions,
                    account_hash,
                    account_creation_date,
                    account_exclude_tags,
                    account_exclude_other_media,
                    account_exclude_other_lists,
                    account_exclude_other_tags,
                    account_exclude_other_processes,
                    key_permissions,
                    key_id
                FROM accounts
                JOIN apikeys ON key_owner = accounts.id
                WHERE key_id = ?
                """,
                JsonArray().add(keyId)
        )
    }

    /**
     * Fetches all general account info about the account with the specified ID
     * @param id The account's ID
     * @return All rows from database search
     * @since 1.0
     */
    suspend fun fetchAccountInfoById(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                id,
                account_email AS email,
                account_name AS name,
                account_admin AS admin,
                account_permissions AS permissions,
                account_creation_date AS creation_date,
                (
                    SELECT
                    COUNT(*)
                    FROM media
                    WHERE media_creator = ?
                ) AS files_created
                FROM accounts
                WHERE id = ?
            """.trimIndent(),
                JsonArray()
                        .add(id)
                        .add(id)
        )
    }

    /**
     * Fetches all admin accounts
     * @return All accounts that are administrators
     * @since 1.0
     */
    suspend fun fetchAdminAccounts(): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT * FROM accounts WHERE account_admin = true
            """.trimIndent(),
                JsonArray()
        )
    }

    /**
     * Updates an account's info
     * @param id The ID of the account
     * @param newName The new name for this account
     * @param newEmail The new email for this account
     * @param isAdmin Whether this account will be an administrator
     * @param newPermissions The new permissions for this account
     * @since 1.0
     */
    suspend fun updateAccountInfo(id: Int, newName: String, newEmail: String, isAdmin: Boolean, newPermissions: JsonArray) {
        client?.queryWithParamsAwait(
                """
                UPDATE accounts
                SET
                	account_name = ?,
                	account_email = ?,
                	account_admin = ?,
                	account_permissions = CAST( ? AS jsonb )
                WHERE id = ?
            """.trimIndent(),
                JsonArray()
                        .add(newName)
                        .add(newEmail)
                        .add(isAdmin)
                        .add(newPermissions.toString())
                        .add(id)
        )
    }

    /**
     * Updates an account's info
     * @param id The ID of the account
     * @param newName The new name for this account
     * @param newEmail The new email for this account
     * @param newHash The new password hash for this account
     * @param excludeTags Tags to globally exclude when listing files (from searches, lists, or anywhere else an array of files would be returned other than file children)
     * @param excludeOtherMedia Whether to globally exclude media created by other users when viewing or listing any media
     * @param excludeOtherLists Whether to globally exclude lists created by other users
     * @param excludeOtherTags Whether to globally exclude tags added to files created by other users
     * @param excludeOtherProcesses Whether to globally exclude processes created by other users
     * @since 1.2.0
     */
    suspend fun updateAccountInfo(id: Int, newName: String, newEmail: String, newHash: String, excludeTags: JsonArray, excludeOtherMedia: Boolean, excludeOtherLists: Boolean, excludeOtherTags: Boolean, excludeOtherProcesses: Boolean) {
        client?.queryWithParamsAwait(
                """
                UPDATE accounts
                SET
                    account_name = ?,
                    account_email = ?,
                    account_hash = ?,
                    account_exclude_tags = CAST( ? AS jsonb ),
                    account_exclude_other_media = ?,
                    account_exclude_other_lists = ?,
                    account_exclude_other_tags = ?,
                    account_exclude_other_processes = ?
                WHERE id = ?
            """.trimIndent(),
                JsonArray()
                        .add(newName)
                        .add(newEmail)
                        .add(newHash)
                        .add(excludeTags.toString())
                        .add(excludeOtherMedia)
                        .add(excludeOtherLists)
                        .add(excludeOtherTags)
                        .add(excludeOtherProcesses)
                        .add(id)
        )
    }

    /**
     * Updates an account's password hash
     * @param id The ID of the account
     * @param newHash The new password hash for this account
     * @since 1.0
     */
    suspend fun updateAccountHash(id: Int, newHash: String) {
        client?.queryWithParamsAwait(
                """
                UPDATE accounts
                SET
                    account_hash = ?
                WHERE id = ?
            """.trimIndent(),
                JsonArray()
                        .add(newHash)
                        .add(id)
        )
    }

    /**
     * Creates a new account entry with the provided details
     * @param email The email address of the new account
     * @param name The name of the new account
     * @param admin Whether the new account will be an administrator
     * @param permissions An array of permissions that the new account will have
     * @param hash The password hash for the new account
     * @since 1.0
     */
    suspend fun createAccountEntry(email: String, name: String, admin: Boolean, permissions: JsonArray, hash: String) {
        client?.queryWithParamsAwait(
                """
                INSERT INTO accounts
                ( account_email, account_name, account_admin, account_permissions, account_hash )
                VALUES
                ( ?, ?, ?, CAST( ? AS jsonb ), ? )
            """.trimIndent(),
                JsonArray()
                        .add(email)
                        .add(name)
                        .add(admin)
                        .add(permissions.toString())
                        .add(hash)
        )
    }

    /**
     * Deletes an account
     * @param id The ID of the account to delete
     * @since 1.0
     */
    suspend fun deleteAccount(id: Int) {
        client?.queryWithParamsAwait(
                """
                DELETE FROM accounts
                WHERE id = ?
            """.trimIndent(),
                JsonArray().add(id)
        )
    }
}