package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.util.UserAccount

/**
 * Database model for API keys
 * @since 1.3.0
 */
class ApiKeysModel {
    /**
     * Creates a new ApiKeysModel
     * @since 1.3.0
     */
    constructor()

    /**
     * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
     * Order key:
     * 0 = Creation date, newest to oldest
     * 1 = Creation date, oldest to newest
     * 2 = Name alphabetically, ascending
     * 3 = Name alphabetically, descending
     * @param order The order
     * @return The appropriate "ORDER BY" SQL for the selected order
     * @since 1.3.0
     */
    private fun orderBy(order: Int): String {
        return "ORDER BY " + when (order) {
            1 -> "key_created_on ASC"
            2 -> "key_name ASC, media_filename ASC"
            3 -> "key_name DESC, media_filename DESC"
            else -> "key_created_on DESC"
        }
    }

    /**
     * Creates a new API key entry
     * @param id The generated alphanumeric key ID
     * @param name The key entry's name
     * @param permissions An array of permissions that this API key grants
     * @param jwt The JWT key String
     * @param owner The ID of the account that owns this API key
     * @since 1.3.0
     */
    suspend fun createApiKey(id: String, name: String, permissions: JsonArray, jwt: String, owner: Int) {
        client?.queryWithParamsAwait(
                """
                INSERT INTO apikeys
                ( key_id, key_name, key_permissions, key_jwt, key_owner )
                VALUES
                ( ?, ?, CAST( ? AS jsonb ), ?, ? )
                """.trimIndent(),
                JsonArray()
                        .add(id)
                        .add(name)
                        .add(permissions.toString())
                        .add(jwt)
                        .add(owner)
        )
    }

    /**
     * Fetches a list of API keys owned by the specified account
     * @param owner The internal ID of the account whose keys are being returned
     * @param offset The offset of the keys to fetch
     * @param limit The amount of keys to return
     * @param order The order to return the API keys
     * @return All API keys in the specified range
     * @since 1.3.0
     */
    suspend fun fetchApiKeyList(owner: Int, offset: Int, limit: Int, order: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    key_id AS id,
                    key_name AS name,
                    key_permissions AS permissions,
                    key_jwt AS jwt,
                    key_owner AS owner,
                    key_created_on AS created_on,
                    account_name AS owner_name
                FROM apikeys
                LEFT JOIN accounts ON accounts.id = key_owner
                WHERE key_owner = ?
                ${orderBy(order)}
                OFFSET ? LIMIT ?
                """.trimIndent(),
                JsonArray()
                        .add(owner)
                        .add(offset)
                        .add(limit)
        )
    }

    /**
     * Fetches info for a single API key based on its alphanumeric ID
     * @param keyId The key's generated alphanumeric ID
     * @return Info for the specified key
     * @since 1.3.0
     */
    suspend fun fetchApiKeyInfo(keyId: String): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    key_id AS id,
                    key_name AS name,
                    key_permissions AS permissions,
                    key_jwt AS jwt,
                    key_owner AS owner,
                    key_created_on AS created_on,
                    account_name AS owner_name
                FROM apikeys
                LEFT JOIN accounts ON accounts.id = key_owner
                WHERE key_id = ?
                """.trimIndent(),
                JsonArray().add(keyId)
        )
    }

    /**
     * Fetches the raw entry for the specified alphanumeric key ID
     * @param keyId The key's generated alphanumeric ID
     * @return The raw entry for the key
     * @since 1.3.0
     */
    suspend fun fetchApiKey(keyId: String): ResultSet? {
        return client?.queryWithParamsAwait(
                "SELECT * FROM apikeys WHERE key_id = ?",
                JsonArray().add(keyId)
        )
    }

    /**
     * Fetches the raw entry for the specified internal key ID
     * @param id The key's internal ID
     * @return The raw entry for the key
     * @since 1.3.0
     */
    suspend fun fetchApiKeyById(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                "SELECT * FROM apikeys WHERE id = ?",
                JsonArray().add(id)
        )
    }

    /**
     * Updates a API key entry's info
     * @param id The internal ID of the key entry
     * @param name The key entry's new name
     * @param permissions The key entry's new array of permissions
     * @since 1.3.0
     */
    suspend fun updateApiKeyEntry(id: Int, name: String, permissions: JsonArray) {
        client?.queryWithParamsAwait(
                """
                UPDATE apikeys
                SET
                	key_name = ?,
                	key_permissions = CAST( ? AS jsonb )
                WHERE id = ?
                """.trimIndent(),
                JsonArray()
                        .add(name)
                        .add(permissions.toString())
                        .add(id)
        )
    }

    /**
     * Deletes the specified API key entry
     * @param id The internal ID of the key to delete
     * @since 1.3.0
     */
    suspend fun deleteApiKey(id: Int) {
        client?.queryWithParamsAwait(
                "DELETE FROM apikeys WHERE id = ?",
                JsonArray().add(id)
        )
    }
}