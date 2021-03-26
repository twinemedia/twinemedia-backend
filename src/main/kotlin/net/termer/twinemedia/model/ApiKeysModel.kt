package net.termer.twinemedia.model

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.db.dataobject.ApiKey
import net.termer.twinemedia.db.dataobject.ApiKeyInfo
import net.termer.twinemedia.util.toJsonArray

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
	 * SELECT statement for getting info
	 * @param extra Extra rows to select (can be null for none)
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect(extra: String?): String {
		return """
			SELECT
				key_id AS id,
				key_name AS name,
				key_permissions AS permissions,
				key_jwt AS jwt,
				key_owner AS owner,
				key_created_on AS created_on,
				account_name AS owner_name
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM apikeys
			LEFT JOIN accounts ON accounts.id = key_owner
		""".trimIndent()
	}
	/**
	 * SELECT statement for getting info
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect() = infoSelect(null)

	/**
	 * Creates a new API key entry
	 * @param id The generated alphanumeric key ID
	 * @param name The key entry's name
	 * @param permissions An array of permissions that this API key grants
	 * @param jwt The JWT key String
	 * @param owner The ID of the account that owns this API key
	 * @since 1.3.0
	 */
	suspend fun createApiKey(id: String, name: String, permissions: Array<String>, jwt: String, owner: Int) {
		SqlTemplate
				.forUpdate(client, """
					INSERT INTO apikeys
					( key_id, key_name, key_permissions, key_jwt, key_owner )
					VALUES
					( #{id}, #{name}, CAST( #{perms} AS jsonb ), #{jwt}, #{owner} )					
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"id" to id,
						"name" to name,
						"perms" to permissions.toJsonArray(),
						"jwt" to jwt,
						"owner" to owner
				)).await()
	}

	/**
	 * Fetches a list of API key info owned by the specified account
	 * @param owner The internal ID of the account whose keys are being returned
	 * @param offset The offset of the keys to fetch
	 * @param limit The amount of keys to return
	 * @param order The order to return the API keys
	 * @return All API keys in the specified range
	 * @since 1.4.0
	 */
	suspend fun fetchApiKeyList(owner: Int, offset: Int, limit: Int, order: Int): RowSet<ApiKeyInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE key_owner = #{owner}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(ApiKeyInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"owner" to owner,
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches info for a single API key based on its alphanumeric ID
	 * @param keyId The key's generated alphanumeric ID
	 * @return Info for the specified key
	 * @since 1.4.0
	 */
	suspend fun fetchApiKeyInfo(keyId: String): RowSet<ApiKeyInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE key_id = #{keyId}	
				""".trimIndent())
				.mapTo(ApiKeyInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"keyId" to keyId
				)).await()
	}

	/**
	 * Fetches the raw entry for the specified alphanumeric key ID
	 * @param keyId The key's generated alphanumeric ID
	 * @return The raw entry for the key
	 * @since 1.4.0
	 */
	suspend fun fetchApiKey(keyId: String): RowSet<ApiKey> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM apikeys WHERE key_id = #{keyId}
				""".trimIndent())
				.mapTo(ApiKey.MAPPER)
				.execute(hashMapOf<String, Any>(
						"keyId" to keyId
				)).await()
	}

	/**
	 * Fetches the raw entry for the specified internal key ID
	 * @param id The key's internal ID
	 * @return The raw entry for the key
	 * @since 1.4.0
	 */
	suspend fun fetchApiKeyById(id: Int): RowSet<ApiKey> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM apikeys WHERE id = #{id}
				""".trimIndent())
				.mapTo(ApiKey.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Updates a API key entry's info
	 * @param id The internal ID of the key entry
	 * @param name The key entry's new name
	 * @param permissions The key entry's new array of permissions
	 * @since 1.3.0
	 */
	suspend fun updateApiKeyEntry(id: Int, name: String, permissions: Array<String>) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE apikeys
					SET
						key_name = #{name},
						key_permissions = CAST( #{perms} AS jsonb )
					WHERE id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"id" to id,
						"name" to name,
						"perms" to permissions.toJsonArray()
				)).await()
	}

	/**
	 * Deletes the specified API key entry
	 * @param id The internal ID of the key to delete
	 * @since 1.3.0
	 */
	suspend fun deleteApiKey(id: Int) {
		SqlTemplate
				.forUpdate(client, """
					DELETE FROM apikeys WHERE id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Deletes all API keys owned by the provided account
	 * @param accountId The account's ID to search for when deleting keys
	 * @since 1.3.0
	 */
	suspend fun deleteApiKeysByAccount(accountId: Int) {
		SqlTemplate
				.forUpdate(client, """
					DELETE FROM apikeys WHERE key_owner = #{accountId}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"accountId" to accountId
				)).await()
	}
}