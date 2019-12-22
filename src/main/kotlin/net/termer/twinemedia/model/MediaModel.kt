package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

/**
 * Creates a new media entry
 * @param id The generated alphanumeric media ID
 * @param filename The media's original filename
 * @param size The size of the media (in bytes)
 * @param mime The mime type of the media
 * @param file The name of the saved media file
 * @since 1.0
 */
suspend fun createMedia(id : String, filename : String, size : Long, mime : String, file : String) {
    client?.queryWithParamsAwait(
            """
                INSERT INTO media
                ( media_id, media_filename, media_size, media_mime, media_file )
                VALUES
                ( ?, ?, ?, ?, ? )
            """.trimIndent(),
            JsonArray()
                    .add(id)
                    .add(filename)
                    .add(size)
                    .add(mime)
                    .add(file)
    )
}

/**
 * Fetches the media entry with the specified generated ID
 * @param mediaId The generated alphanumeric media ID to search for
 * @return All rows from database search
 * @since 1.0
 */
suspend fun fetchMedia(mediaId : String) : ResultSet? {
    return client?.queryWithParamsAwait(
            "SELECT * FROM media WHERE media_id = ?",
            JsonArray().add(mediaId)
    )
}