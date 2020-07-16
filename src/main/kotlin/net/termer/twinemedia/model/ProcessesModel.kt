package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.util.UserAccount

/**
 * Database model for process presets
 * @since 1.2.0
 */
class ProcessesModel {
    private var _account: UserAccount? = null

    /**
     * The account associated with this model instance
     * @since 1.2.0
     */
    var account: UserAccount?
        get() = _account
        set(value) { _account = value }

    /**
     * Creates a new ProcessesModel with an account
     * @param account The account to use for this model instance
     * @since 1.2.0
     */
    constructor(account: UserAccount) {
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
     * 2 = Name alphabetically, ascending
     * 3 = Name alphabetically, descending
     * 4 = Size, largest to smallest
     * 5 = Size, smallest to largest
     * @param order The order
     * @return The appropriate "ORDER BY" SQL for the selected order
     * @since 1.0
     */
    private fun orderBy(order: Int): String {
        return "ORDER BY " + when (order) {
            1 -> "media_created_on ASC"
            2 -> "media_name ASC, media_filename ASC"
            3 -> "media_name DESC, media_filename DESC"
            4 -> "media_size DESC"
            5 -> "media_size ASC"
            else -> "media_created_on DESC"
        }
    }

    /**
     * Applies a default SQL WHERE filter for listing process presets based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun listWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherProcesses -> {
                "process_creator = ${account?.id}"
            }
            account!!.hasPermission("processes.list.all") -> {
                "TRUE"
            }
            else -> {
                "process_creator = ${account?.id}"
            }
        }
    }
    /**
     * Applies a default SQL WHERE filter for fetching single process presets based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun viewWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherProcesses -> {
                "process_creator = ${account?.id}"
            }
            account!!.hasPermission("processes.view.all") -> {
                "TRUE"
            }
            else -> {
                "process_creator = ${account?.id}"
            }
        }
    }
    /**
     * Applies a default SQL WHERE filter for fetching single process presets for editing based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun editWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherProcesses -> {
                "process_creator = ${account?.id}"
            }
            account!!.hasPermission("processes.edit.all") -> {
                "TRUE"
            }
            else -> {
                "process_creator = ${account?.id}"
            }
        }
    }
    /**
     * Applies a default SQL WHERE filter for fetching single process presets for deleting based on the user object associated with this model
     * @return a default SQL WHERE filter to insert into a query
     * @since 1.2.0
     */
    private fun deleteWhereFilter(): String {
        return when {
            account == null -> {
                "TRUE"
            }
            account!!.excludeOtherProcesses -> {
                "process_creator = ${account?.id}"
            }
            account!!.hasPermission("processes.delete.all") -> {
                "TRUE"
            }
            else -> {
                "process_creator = ${account?.id}"
            }
        }
    }

    /**
     * Creates a new process setting entry
     * @param mime The mime this process setting is for
     * @param settings The settings for this process
     * @param creator The ID of the creator of this process
     * @since 1.0
     */
    suspend fun createProcess(mime: String, settings: JsonObject, creator: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                INSERT INTO processes
                ( process_mime, process_settings, process_creator )
                VALUES
                ( ?, CAST( ? AS jsonb), ? )
            """.trimIndent(),
                JsonArray()
                        .add(mime)
                        .add(settings.toString())
                        .add(creator)
        )
    }

    /**
     * Fetches all data for the specified process
     * @param id The ID of the process to fetch
     * @return All data for the specified process
     */
    suspend fun fetchProcess(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT * FROM processes WHERE ${viewWhereFilter()} AND id = ?
            """.trimIndent(),
                JsonArray().add(id)
        )
    }

    /**
     * Fetches info for the specified process
     * @param id The ID of the process to fetch
     * @return Info for the specified process
     */
    suspend fun fetchProcessInfo(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    processes.id,
                    process_mime AS mime,
                    process_settings AS settings,
                    process_created_on AS created_on,
                    process_creator AS creator,
                    account_name AS creator_name
                FROM processes
                LEFT JOIN accounts ON accounts.id = process_creator
                WHERE
                ${viewWhereFilter()}
                AND processes.id = ?
            """.trimIndent(),
                JsonArray().add(id)
        )
    }

    /**
     * Fetches info for all processes
     * @param offset The offset of rows to return
     * @param limit The amount of rows to return
     * @return Info for all processes
     */
    suspend fun fetchProcesses(offset: Int, limit: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    processes.id,
                    process_mime AS mime,
                    process_settings AS settings,
                    process_created_on AS created_on,
                    process_creator AS creator,
                    account_name AS creator_name
                FROM processes
                LEFT JOIN accounts ON accounts.id = process_creator
                WHERE ${listWhereFilter()}
                OFFSET ? LIMIT ?
            """.trimIndent(),
                JsonArray()
                        .add(offset)
                        .add(limit)
        )
    }

    /**
     * Fetches all process presets for the specified mime
     * @param mime The mime to find processes for
     * @return All processes for the specified mime
     * @since 1.0
     */
    suspend fun fetchProcessesForMime(mime: String): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    processes.id,
                    process_mime AS mime,
                    process_settings AS settings,
                    trim('"' FROM (process_settings::jsonb->'extension')::text) AS extension,
                    process_created_on AS created_on,
                    process_creator AS creator,
                    account_name AS creator_name
                FROM processes
                LEFT JOIN accounts ON accounts.id = process_creator
                WHERE
                ${listWhereFilter()}
                AND process_mime = ?
            """.trimIndent(),
                JsonArray().add(mime)
        )
    }

    /**
     * Fetches all process presets for the specified mime account
     * @param mime The mime to find processes for
     * @param account The ID of the account to fetch process presets for
     * @return All processes for the specified mime
     * @since 1.0
     */
    suspend fun fetchProcessesForMimeAndAccount(mime: String, account: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                SELECT
                    processes.id,
                    process_mime AS mime,
                    process_settings AS settings,
                    trim('"' FROM (process_settings::jsonb->'extension')::text) AS extension,
                    process_created_on AS created_on,
                    process_creator AS creator,
                    account_name AS creator_name
                FROM processes
                LEFT JOIN accounts ON accounts.id = process_creator
                WHERE
                ${listWhereFilter()}
                AND process_mime = ?
                AND process_creator = ?
            """.trimIndent(),
                JsonArray()
                        .add(mime)
                        .add(account)
        )
    }

    /**
     * Updates data for a process
     * @param id The ID of the process
     * @param mime The new mime
     * @param settings The new settings
     * @since 1.0
     */
    suspend fun updateProcess(id: Int, mime: String, settings: JsonObject): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                UPDATE processes
                SET
                    process_mime = ?,
                    process_settings = CAST( ? AS jsonb )
                WHERE
                ${editWhereFilter()}
                AND id = ?
            """.trimIndent(),
                JsonArray()
                        .add(mime)
                        .add(settings.toString())
                        .add(id)
        )
    }

    /**
     * Deletes a process entry
     * @param id The ID of the process to delete
     * @since 1.0
     */
    suspend fun deleteProcess(id: Int): ResultSet? {
        return client?.queryWithParamsAwait(
                """
                DELETE FROM processes WHERE ${deleteWhereFilter()} AND id = ?
            """.trimIndent(),
                JsonArray().add(id)
        )
    }
}