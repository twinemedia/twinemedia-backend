package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

/**
 * Creates a new process setting entry
 * @param mime The mime this process setting is for
 * @param settings The settings for this process
 * @param creator The ID of the creator of this process
 * @since 1.0
 */
suspend fun createProcess(mime : String, settings : JsonObject, creator : Int) : ResultSet? {
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
suspend fun fetchProcess(id: Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT * FROM processes WHERE id = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}

/**
 * Fetches info for the specified process
 * @param id The ID of the process to fetch
 * @return Info for the specified process
 */
suspend fun fetchProcessInfo(id: Int) : ResultSet? {
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
                WHERE processes.id = ?
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
suspend fun fetchProcesses(offset : Int, limit : Int) : ResultSet? {
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
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches all processes for the specified mime
 * @param mime The mime to find processes for
 * @return All processes for the specified mime
 * @since 1.0
 */
suspend fun fetchProcessesForMime(mime : String) : ResultSet? {
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
                WHERE process_mime = ?
            """.trimIndent(),
            JsonArray().add(mime)
    )
}

/**
 * Updates data for a process
 * @param id The ID of the process
 * @param mime The new mime
 * @param settings The new settings
 * @since 1.0
 */
suspend fun updateProcess(id : Int, mime: String, settings: JsonObject) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                UPDATE processes
                SET
                    process_mime = ?,
                    process_settings = CAST( ? AS jsonb )
                WHERE id = ?
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
suspend fun deleteProcess(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                DELETE FROM processes WHERE id = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}