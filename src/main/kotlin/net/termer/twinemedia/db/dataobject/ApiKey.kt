package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toJsonArray
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for an API key
 * @param internalId The key's internal ID, the one not exposed through the API
 * @param id The key's alphanumeric ID
 * @param name The user-defined name of the key
 * @param permissions The permissions this key was granted
 * @param jwt The key's JWT authentication token
 * @param owner The owner of this key, the subject of the JWT token
 * @param createdOn The time this key was created
 * @since 1.4.0
 */
@DataObject
class ApiKey(
		/**
		 * The key's internal ID, the one not exposed through the API
		 * @since 1.4.0
		 */
		val internalId: Int,
		/**
		 * The key's alphanumeric ID
		 * @since 1.4.0
		 */
		val id: String,
		/**
		 * The user-defined name of the key
		 * @since 1.4.0
		 */
		val name: String,
		/**
		 * The permissions this key was granted
		 * @since 1.4.0
		 */
		val permissions: Array<String>,
		/**
		 * The key's JWT authentication token
		 * @since 1.4.0
		 */
		val jwt: String,
		/**
		 * The owner of this key, the subject of the JWT token
		 * @since 1.4.0
		 */
		val owner: Int,
		/**
		 * The time this key was created
		 * @since 1.4.0
		 */
		val createdOn: OffsetDateTime
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<ApiKey> { row ->
			ApiKey(
					internalId = row.getInteger("id"),
					id = row.getString("key_id"),
					name = row.getString("key_name"),
					permissions = row.getJsonArray("key_permissions").toStringArray(),
					jwt = row.getString("key_jwt"),
					owner = row.getInteger("key_owner"),
					createdOn = row.getOffsetDateTime("key_created_on")
			)
		}
	}
}