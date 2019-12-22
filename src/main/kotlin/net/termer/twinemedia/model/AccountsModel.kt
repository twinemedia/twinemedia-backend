package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

/**
 * Fetches all info about an account with the specified email
 * @param email The email to search
 * @return All rows from database search
 * @since 1.0
 */
suspend fun fetchAccountByEmail(email : String) : ResultSet? {
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
suspend fun fetchAccountById(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            "SELECT * FROM accounts WHERE id = ?",
            JsonArray().add(id)
    )
}

/**
 * Fetches all general account info about the account with the specified ID
 * @param id The account's ID
 * @return All rows from database search
 * @since 1.0
 */
suspend fun fetchAccountInfoById(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                	account_email as email,
                	account_admin as admin,
                	account_name as name,
                	account_permissions as permissions
                FROM accounts
                WHERE id = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}