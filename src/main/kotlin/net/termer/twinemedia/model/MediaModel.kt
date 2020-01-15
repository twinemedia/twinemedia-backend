package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryAwait
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

/**
 * Creates a new media entry
 * @param id The generated alphanumeric media ID
 * @param filename The media's original filename
 * @param size The size of the media (in bytes)
 * @param mime The mime type of the media
 * @param file The name of the saved media file
 * @param creator The ID of the account that uploaded this media file
 * @since 1.0
 */
suspend fun createMedia(id : String, filename : String, size : Long, mime : String, file : String, creator : Int, hash : String) {
    client?.queryWithParamsAwait(
            """
                INSERT INTO media
                ( media_id, media_filename, media_size, media_mime, media_file, media_creator, media_file_hash )
                VALUES
                ( ?, ?, ?, ?, ?, ?, ? )
            """.trimIndent(),
            JsonArray()
                    .add(id)
                    .add(filename)
                    .add(size)
                    .add(mime)
                    .add(file)
                    .add(creator)
                    .add(hash)
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

/**
 * Fetches a list of media
 * @param offset The offset of the media to fetch
 * @param limit The amount of media to return
 * @return All media files in the specified range
 * @since 1.0
 */
suspend fun fetchMediaList(offset : Int, limit : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                    media_id AS id,
                    media_name AS name,
                    media_filename AS filename,
                    media_size AS size,
                    media_mime AS mime,
                    media_created_on AS created_on,
                    media_creator AS creator,
                    media_file_hash AS file_hash,
                    account_name AS creator_name
                FROM media
                JOIN accounts ON accounts.id = media_creator
                WHERE media_parent IS NULL
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches info about a media file. Note: the returned "internal_id" field should be removed before serving info the the client.
 * @param mediaId The alphanumberic generated file ID to search
 * @return The info for the specified media file
 * @since 1.0
 */
suspend fun fetchMediaInfo(mediaId : String) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                	media.id AS internal_id,
                    media_parent AS internal_parent,
                	media_id AS id,
                	media_name AS name,
                	media_filename AS filename,
                	media_size AS size,
                	media_mime AS mime,
                	media_created_on AS created_on,
                	media_creator AS creator,
                    media_description AS description,
                    media_meta AS meta,
                    media_tags AS tags,
                    media_file_hash AS file_hash,
                	account_name AS creator_name
                FROM media
                JOIN accounts ON accounts.id = media_creator
                WHERE media_id = ?
            """.trimIndent(),
            JsonArray().add(mediaId)
    )
}

/**
 * Fetches info about a media file. Note: the returned "internal_id" field should be removed before serving info the the client.
 * @param id The media file's internal ID
 * @return The info for the specified media file
 * @since 1.0
 */
suspend fun fetchMediaInfo(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                	media.id AS internal_id,
                	media_id AS id,
                	media_name AS name,
                	media_filename AS filename,
                	media_size AS size,
                	media_mime AS mime,
                	media_parent AS internal_parent,
                	media_created_on AS created_on,
                	media_creator AS creator,
                    media_description AS description,
                    media_meta AS meta,
                    media_tags AS tags,
                    media_file_hash AS file_hash,
                	account_name AS creator_name
                FROM media
                JOIN accounts ON accounts.id = media_creator
                WHERE media.id = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}

/**
 * Fetches a media file's child generated alphanumeric IDs
 * @param id The internal media ID of the media file
 * @return All child IDs of the specified media fiel
 * @since 1.0
 */
suspend fun fetchMediaChildren(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                    media_id AS id,
                    media_name AS name,
                    media_filename AS filename,
                    media_size AS size,
                    media_mime AS mime,
                    media_created_on AS created_on,
                    media_creator AS creator,
                    media_file_hash AS file_hash,
                    account_name AS creator_name
                FROM media
                JOIN accounts ON accounts.id = media_creator
                WHERE media_parent = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}

/**
 * Updates a media file's info
 * @param id The internal media ID of the media file
 * @param newMediaId The new alphanumeric ID for this media file
 * @param newName The new name for this media file
 * @param newDesc The new description for this media file
 * @param newTags The new tags for this media
 * @since 1.0
 */
suspend fun updateMediaInfo(id : Int, newName : String?, newDesc : String?, newTags : JsonArray) {
    client?.queryWithParamsAwait(
            """
                UPDATE media
                SET
                	media_name = ?,
                	media_description = ?,
                	media_tags = CAST( ? AS jsonb )
                WHERE id = ?
            """.trimIndent(),
            JsonArray()
                    .add(newName)
                    .add(newDesc)
                    .add(newTags.toString())
                    .add(id)
    )
}

/**
 * Fetches all media files with the provided hash
 * @param hash The file hash to search for
 * @return All media files with the specified hash
 * @since 1.0
 */
suspend fun fetchMediaByHash(hash : String) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT * FROM media
                WHERE media_file_hash = ?
            """.trimIndent(),
            JsonArray().add(hash)
    )
}

/**
 * Fetches a list of media files by tags
 * @param tags The tags to search for
 * @param offset The offset of rows to return
 * @param limit The amount of rows to return
 * @return The all media file info entries that contain the specified tags
 * @since 1.0
 */
suspend fun fetchMediaListByTags(tags : JsonArray, offset : Int, limit : Int) : ResultSet? {
    var sql = """
                SELECT
                    media_id AS id,
                    media_name AS name,
                    media_filename AS filename,
                    media_size AS size,
                    media_mime AS mime,
                    media_created_on AS created_on,
                    media_creator AS creator,
                    media_file_hash AS file_hash,
                    account_name AS creator_name
                FROM media
                JOIN accounts ON accounts.id = media_creator
                WHERE media_parent IS NULL
            """.trimIndent()

    // Add AND statements for tags
    for(tag in tags)
        sql += "\nAND media_tags::jsonb ?? ?"

    // Offset, limit
    sql += "\nOFFSET ? LIMIT ?"

    return client?.queryWithParamsAwait(
            sql,
            tags
                    .add(offset)
                    .add(limit)
    )
}