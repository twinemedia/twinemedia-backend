package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.JsonSerializable
import java.time.OffsetDateTime

/**
 * DTO for an account
 * @since 2.0.0
 */
class AccountDto(
	/**
	 * The account's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: Int,

	/**
	 * The account's email address
	 * @since 2.0.0
	 */
	val email: String,

	/**
	 * The account's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * An array of the account's permissions
	 * @since 2.0.0
	 */
	val permissions: Array<String>,

	/**
	 * Whether the account is an admin
	 * @since 2.0.0
	 */
	val isAdmin: Boolean,

	/**
	 * The account's default file source, or null if none
	 * @since 2.0.0
	 */
	val defaultSource: RecordSourceDto?,

	/**
	 * The number of files the account has created
	 * @since 2.0.0
	 */
	val fileCount: Int,

	/**
	 * The account's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime,

	/**
	 * The account's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
): JsonSerializable() {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"email" to email,
		"name" to name,
		"permissions" to permissions,
		"isAdmin" to isAdmin,
		"defaultSource" to defaultSource?.toJson(),
		"fileCount" to fileCount,
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 2.0.0
		 */
		val MAPPER = RowMapper { row ->
			val defaultSourceId = row.getString("account_default_source_id")

			AccountDto(
				id = row.getInteger("account_id"),
				email = row.getString("account_email"),
				name = row.getString("account_name"),
				isAdmin = row.getBoolean("account_admin"),
				permissions = row.getArrayOfStrings("account_permissions"),
				defaultSource = if(defaultSourceId == null) null else RecordSourceDto(
					id = defaultSourceId,
					name = row.getString("account_default_source_name"),
					type = row.getString("account_default_source_type")
				),
				fileCount = row.getInteger("account_file_count"),
				createdTs = row.getOffsetDateTime("account_created_ts"),
				modifiedTs = row.getOffsetDateTime("account_modified_ts")
			)
		}
	}
}