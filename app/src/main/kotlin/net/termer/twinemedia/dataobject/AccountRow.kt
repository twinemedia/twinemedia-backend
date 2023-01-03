package net.termer.twinemedia.dataobject

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.Row
import net.termer.twinemedia.util.containsPermission
import net.termer.twinemedia.util.db.hasCol
import java.time.OffsetDateTime

/**
 * Data class for an account
 * @since 2.0.0
 */
class AccountRow(
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
     * The account's password hash
     * @since 2.0.0
     */
    val hash: String,

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
	 * The account's maximum allowed file upload size, or null to use the application configured value.
	 * This value overrides the application's configured value.
	 * @since 2.0.0
	 */
	val maxUploadSize: Long?,

	/**
	 * The account's maximum allowed number of concurrent file uploads, or null to use the application configured value
	 * This value overrides the application's configured value.
	 * @since 2.0.0
	 */
	val maxConcurrentUploads: Int?,

	/**
     * The ID of this account's default file source internal ID, or null if none
     * @since 2.0.0
     */
    val defaultSourceInternalId: Int?,

	/**
	 * The number of files owned by the account
	 * @since 2.0.0
	 */
	val fileCount: Int,

	override val createdTs: OffsetDateTime,
	override val modifiedTs: OffsetDateTime
): StandardRow {
	companion object {
		/**
		 * Maps a row to a new object instance
		 * @since 2.0.0
		 */
		fun fromRow(row: Row) = AccountRow(
			internalId = row.getInteger("id"),
			id = row.getString("account_id"),
			email = row.getString("account_email"),
			name = row.getString("account_name"),
			permissions = row.getArrayOfStrings("account_permissions"),
			isAdmin = row.getBoolean("account_admin"),
			hash = row.getString("account_hash"),
			excludeTags = row.getArrayOfStrings("account_exclude_tags"),
			excludeOtherFiles = row.getBoolean("account_exclude_other_files"),
			excludeOtherLists = row.getBoolean("account_exclude_other_lists"),
			excludeOtherTags = row.getBoolean("account_exclude_other_tags"),
			excludeOtherProcessPresets = row.getBoolean("account_exclude_other_process_presets"),
			excludeOtherSources = row.getBoolean("account_exclude_other_sources"),
			defaultSourceInternalId = row.getInteger("account_default_source"),
			maxUploadSize = row.getLong("account_max_upload_size"),
			maxConcurrentUploads = row.getInteger("account_max_concurrent_uploads"),
			fileCount = row.getInteger("account_file_count"),
			createdTs = row.getOffsetDateTime("account_created_ts"),
			modifiedTs = row.getOffsetDateTime("account_modified_ts")
		)
	}
}
