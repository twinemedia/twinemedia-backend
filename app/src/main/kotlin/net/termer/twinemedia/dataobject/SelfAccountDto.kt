package net.termer.twinemedia.dataobject

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.containsPermission
import net.termer.twinemedia.util.db.hasCol
import net.termer.twinemedia.util.toJsonArray
import java.time.OffsetDateTime

/**
 * DTO for a user's self-account info
 * @since 2.0.0
 */
class SelfAccountDto(
	override val internalId: Int,
	override val id: String,

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
	 * The tags to exclude globally when listing and searching files
	 * @since 2.0.0
	 */
	val excludeTags: Array<String>,

	/**
	 * Whether to globally exclude files created by other accounts
	 * @since 2.0.0
	 */
	val excludeOtherFiles: Boolean,

	/**
	 * Whether to globally exclude lists created by other accounts
	 * @since 2.0.0
	 */
	val excludeOtherLists: Boolean,

	/**
	 * Whether to globally exclude tags on files created by other accounts
	 * @since 2.0.0
	 */
	val excludeOtherTags: Boolean,

	/**
	 * Whether to globally exclude process presets created by other accounts
	 * @since 2.0.0
	 */
	val excludeOtherProcessPresets: Boolean,

	/**
	 * Whether to globally exclude file sources created by other accounts
	 * @since 2.0.0
	 */
	val excludeOtherSources: Boolean,

	/**
	 * Whether this account is being accessed by an API key
	 * @since 2.0.0
	 */
	val isApiKey: Boolean = false,

	/**
	 * An array of permissions that this key is authorized to use, or null if the account is not being accessed by an API key
	 * @since 2.0.0
	 */
	val keyPermissions: Array<String>? = null,

	/**
	 * The account's default file source, or null if none
	 * @since 2.0.0
	 */
	val defaultSource: RecordSourceDto?,

	/**
	 * The account's maximum allowed file upload size.
	 * The value will be -1 if it was null, and therefore should be assigned from the application config.
	 * This value overrides the application's configured value.
	 * @since 2.0.0
	 */
	var maxUploadSize: Long,

	/**
	 * The account's maximum allowed number of concurrent file uploads.
	 * The value will be -1 if it was null, and therefore should be assigned from the application config.
	 * This value overrides the application's configured value.
	 * @since 2.0.0
	 */
	var maxConcurrentUploads: Int,

	/**
	 * The number of files owned by the account
	 * @since 2.0.0
	 */
	val fileCount: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): StandardRow, JsonSerializable() {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"email" to email,
		"name" to name,
		"permissions" to permissions,
		"isAdmin" to isAdmin,
		"excludeTags" to excludeTags.toJsonArray(),
		"excludeOtherFiles" to excludeOtherFiles,
		"excludeOtherLists" to excludeOtherLists,
		"excludeOtherTags" to excludeOtherTags,
		"excludeOtherProcessPresets" to excludeOtherProcessPresets,
		"excludeOtherSources" to excludeOtherSources,
		"isApiKey" to isApiKey,
		"keyPermissions" to keyPermissions?.toJsonArray(),
		"defaultSource" to defaultSource?.toJson(),
		"maxUploadSize" to maxUploadSize,
		"maxConcurrentUploads" to maxConcurrentUploads,
		"fileCount" to fileCount,
		"createdTs" to createdTs.toString(),
		"modifiedTs" to modifiedTs.toString()
	)

	/**
	 * Sets missing values in the DTO using the provided [AppConfig].
	 * Currently, the values [maxUploadSize] and [maxConcurrentUploads] are set using this method if they are -1.
	 * @param config The config to use
	 * @since 2.0.0
	 */
	fun setMissingValues(config: AppConfig) {
		if (maxUploadSize < 0)
			maxUploadSize = config.maxUploadSize
		if (maxConcurrentUploads < 0)
			maxConcurrentUploads = config.maxConcurrentUploads
	}

	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row): SelfAccountDto {
			val isApiKey = row.hasCol("key_permissions")
			val defaultSourceId = row.getString("account_default_source_id")

			return SelfAccountDto(
				internalId = row.getInteger("id"),
				id = row.getString("account_id"),
				email = row.getString("account_email"),
				name = row.getString("account_name"),
				permissions = row.getArrayOfStrings("account_permissions"),
				isAdmin = row.getBoolean("account_admin"),
				excludeTags = row.getArrayOfStrings("account_exclude_tags"),
				excludeOtherFiles = row.getBoolean("account_exclude_other_files"),
				excludeOtherLists = row.getBoolean("account_exclude_other_lists"),
				excludeOtherTags = row.getBoolean("account_exclude_other_tags"),
				excludeOtherProcessPresets = row.getBoolean("account_exclude_other_process_presets"),
				excludeOtherSources = row.getBoolean("account_exclude_other_sources"),
				isApiKey = isApiKey,
				keyPermissions = if(isApiKey)
					row.getArrayOfStrings("key_permissions")
				else null,
				defaultSource = if(defaultSourceId == null) null else RecordSourceDto(
					id = defaultSourceId,
					name = row.getString("account_default_source_name"),
					type = row.getString("account_default_source_type")
				),
				maxUploadSize = row.getLong("account_max_upload_size") ?: -1,
				maxConcurrentUploads = row.getInteger("account_max_concurrent_uploads") ?: -1,
				fileCount = row.getInteger("account_file_count"),
				createdTs = row.getOffsetDateTime("account_created_ts"),
				modifiedTs = row.getOffsetDateTime("account_modified_ts")
			)
		}
	}
}