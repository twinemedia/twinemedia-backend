package net.termer.twinemedia.model

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.dataobject.ProcessPreset
import net.termer.twinemedia.dataobject.ProcessPresetInfo

/**
 * Database model for process presets
 * @since 1.2.0
 */
class ProcessesModel {
	private var _account: AccountRow? = null

	/**
	 * The account associated with this model instance
	 * @since 1.2.0
	 */
	var account: AccountRow?
		get() = _account
		set(value) { _account = value }

	/**
	 * Creates a new ProcessesModel with an account
	 * @param account The account to use for this model instance
	 * @since 1.2.0
	 */
	constructor(account: AccountRow) {
		this.account = account
	}
	/**
	 * Creates a new ProcessesModel without an account
	 * @since 1.2.0
	 */
	constructor() {
		this.account = null
	}

	/**
	 * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
	 * Order key:
	 * 0 = Creation date, newest to oldest
	 * 1 = Creation date, oldest to newest
	 * 2 = MIME alphabetically, ascending
	 * 3 = MIME alphabetically, descending
	 * 4 = Creator ID, highest to lowest
	 * 5 = Creator ID, lowest to highest
	 * 6 = Modified date, newest to oldest
	 * 7 = Modified date, oldest to newest
	 * @param order The order
	 * @return The appropriate "ORDER BY" SQL for the selected order
	 * @since 1.0
	 */
	private fun orderBy(order: Int): String {
		return "ORDER BY " + when(order) {
			1 -> "process_created_on ASC"
			2 -> "process_mime ASC, media_filename ASC"
			3 -> "process_mime DESC, media_filename DESC"
			4 -> "process_creator DESC"
			5 -> "process_creator ASC"
			6 -> "process_modified_on DESC"
			7 -> "process_modified_on ASC"
			else -> "process_created_on DESC"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for listing process presets based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun listWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherProcesses -> "process_creator = ${account?.id}"
			account!!.hasPermission("processes.list.all") -> "TRUE"
			else -> "process_creator = ${account?.id}"
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single process presets based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun viewWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherProcesses -> "process_creator = ${account?.id}"
			account!!.hasPermission("processes.view.all") -> "TRUE"
			else -> "process_creator = ${account?.id}"
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single process presets for editing based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun editWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherProcesses -> "process_creator = ${account?.id}"
			account!!.hasPermission("processes.edit.all") -> "TRUE"
			else -> "process_creator = ${account?.id}"
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single process presets for deleting based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun deleteWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherProcesses -> "process_creator = ${account?.id}"
			account!!.hasPermission("processes.delete.all") -> "TRUE"
			else -> "process_creator = ${account?.id}"
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
				processes.id,
				process_mime AS mime,
				process_settings AS settings,
				trim('"' FROM (process_settings::jsonb->'extension')::text) AS extension,
				process_creator AS creator,
				account_name AS creator_name,
				process_created_on AS created_on,
				process_modified_on AS modified_on
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM processes
			LEFT JOIN accounts ON accounts.id = process_creator
		""".trimIndent()
	}
	/**
	 * SELECT statement for getting info
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect() = infoSelect(null)

	/**
	 * Creates a new process setting entry
	 * @param mime The mime this process setting is for
	 * @param settings The settings for this process
	 * @param creator The ID of the creator of this process
	 * @return The newly created process setting entry's ID
	 * @since 1.5.0
	 */
	suspend fun createProcess(mime: String, settings: JsonObject, creator: Int): Int {
		return SqlTemplate
				.forQuery(client, """
					INSERT INTO processes
					( process_mime, process_settings, process_creator )
					VALUES
					( #{mime}, CAST(#{settings} AS jsonb), #{creator} )
					RETURNING id
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"mime" to mime,
						"settings" to settings,
						"creator" to creator
				)).await()
				.first().getInteger("id")
	}

	/**
	 * Fetches all data for the specified process
	 * @param id The ID of the process to fetch
	 * @return All data for the specified process
	 * @since 1.4.0
	 */
	suspend fun fetchProcess(id: Int): RowSet<ProcessPreset> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM processes WHERE ${viewWhereFilter()} AND id = #{id}
				""".trimIndent())
				.mapTo(ProcessPreset.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches info for the specified process
	 * @param id The ID of the process to fetch
	 * @return Info for the specified process
	 * @since 1.4.0
	 */
	suspend fun fetchProcessInfo(id: Int): RowSet<ProcessPresetInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${viewWhereFilter()}
					AND processes.id = #{id}
				""".trimIndent())
				.mapTo(ProcessPresetInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches info for all processes
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the processes
	 * @return Info for all processes
	 * @since 1.4.0
	 */
	suspend fun fetchProcesses(offset: Int, limit: Int, order: Int): RowSet<ProcessPresetInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE ${listWhereFilter()}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(ProcessPresetInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches all process presets for the specified mime
	 * @param mime The mime to find processes for
	 * @return All processes for the specified mime
	 * @since 1.4.0
	 */
	suspend fun fetchProcessesForMime(mime: String): RowSet<ProcessPresetInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${listWhereFilter()}
					AND process_mime = #{mime}
				""".trimIndent())
				.mapTo(ProcessPresetInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"mime" to mime
				)).await()
	}

	/**
	 * Fetches all process presets for the specified mime type and account
	 * @param mime The mime to find processes for
	 * @param account The ID of the account to fetch process presets for
	 * @return All processes for the specified mime
	 * @since 1.4.0
	 */
	suspend fun fetchProcessesForMimeAndAccount(mime: String, account: Int): RowSet<ProcessPresetInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${listWhereFilter()}
					AND #{mime} LIKE process_mime 
					AND process_creator = #{account}
				""".trimIndent())
				.mapTo(ProcessPresetInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"mime" to mime,
						"account" to account
				)).await()
	}

	/**
	 * Updates data for a process
	 * @param id The ID of the process
	 * @param mime The new mime
	 * @param settings The new settings
	 * @since 1.4.0
	 */
	suspend fun updateProcess(id: Int, mime: String, settings: JsonObject) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE processes
					SET
						process_mime = #{mime},
						process_settings = CAST( #{settings} AS jsonb ),
						process_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"id" to id,
						"mime" to mime,
						"settings" to settings
				)).await()
	}

	/**
	 * Deletes a process entry
	 * @param id The ID of the process to delete
	 * @since 1.4.0
	 */
	suspend fun deleteProcess(id: Int) {
		SqlTemplate
				.forUpdate(client, """
					DELETE FROM processes WHERE ${deleteWhereFilter()} AND id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}
}