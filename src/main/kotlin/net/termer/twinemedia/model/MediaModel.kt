package net.termer.twinemedia.model

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.ext.sql.queryAwait
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import net.termer.twinemedia.db.Database.client

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
private fun orderBy(order : Int) : String {
    return "ORDER BY " + when(order) {
        1 -> "media_created_on ASC"
        2 -> "media_name ASC, media_filename ASC"
        3 -> "media_name DESC, media_filename DESC"
        4 -> "media_size DESC"
        5 -> "media_size ASC"
        else -> "media_created_on DESC"
    }
}

/**
 * Creates a new media entry
 * @param id The generated alphanumeric media ID
 * @param filename The media's original filename
 * @param size The size of the media (in bytes)
 * @param mime The mime type of the media
 * @param file The name of the saved media file
 * @param creator The ID of the account that uploaded this media file
 * @param thumbnailFile The filename of the media's generated thumbnail, null if none
 * @since 1.0
 */
suspend fun createMedia(id : String, filename : String, size : Long, mime : String, file : String, creator : Int, hash : String, thumbnailFile : String?, meta : JsonObject) {
    client?.queryWithParamsAwait(
            """
                INSERT INTO media
                ( media_id, media_filename, media_size, media_mime, media_file, media_creator, media_file_hash, media_thumbnail, media_thumbnail_file, media_meta )
                VALUES
                ( ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST( ? AS jsonb ) )
            """.trimIndent(),
            JsonArray()
                    .add(id)
                    .add(filename)
                    .add(size)
                    .add(mime)
                    .add(file)
                    .add(creator)
                    .add(hash)
                    .add(thumbnailFile != null)
                    .add(thumbnailFile)
                    .add(meta.toString())

    )
}

/**
 * Creates a new media entry
 * @param id The generated alphanumeric media ID
 * @param name The media file's name (can be null)
 * @param filename The media's original filename
 * @param size The size of the media (in bytes)
 * @param mime The mime type of the media
 * @param file The name of the saved media file
 * @param creator The ID of the account that uploaded this media file
 * @param thumbnailFile The filename of the media's generated thumbnail, null if none
 * @param parent The parent of this media file
 * @param processing Whether the media file is processing
 * @since 1.0
 */
suspend fun createMedia(id : String, name : String?, filename : String, size : Long, mime : String, file : String, creator : Int, hash : String, thumbnailFile : String?, meta : JsonObject, parent : Int, processing : Boolean) {
    client?.queryWithParamsAwait(
            """
                INSERT INTO media
                ( media_id, media_name, media_filename, media_size, media_mime, media_file, media_creator, media_file_hash, media_thumbnail, media_thumbnail_file, media_meta, media_parent, media_processing )
                VALUES
                ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST( ? AS jsonb ), ?, ? )
            """.trimIndent(),
            JsonArray()
                    .add(id)
                    .add(name)
                    .add(filename)
                    .add(size)
                    .add(mime)
                    .add(file)
                    .add(creator)
                    .add(hash)
                    .add(thumbnailFile != null)
                    .add(thumbnailFile)
                    .add(meta.toString())
                    .add(parent)
                    .add(processing)

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
 * @param mime The media MIME pattern (allows % for use as wildcards)
 * @param order The order to return the media files
 * @return All media files in the specified range
 * @since 1.0
 */
suspend fun fetchMediaList(offset : Int, limit : Int, mime : String, order : Int) : ResultSet? {
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
                    media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                    account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE media_parent IS NULL AND
                media_mime LIKE ?
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(mime)
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches info about a media file. Note: the returned "internal_id" field should be removed before serving info the the client.
 * @param mediaId The alphanumeric generated file ID to search
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
                    media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS process_error,
                	account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
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
                    media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                	account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE media.id = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}

/**
 * Fetches info for a media file's children
 * @param id The internal media ID of the media file
 * @return All info for the children of the specified media file
 * @since 1.0
 */
suspend fun fetchMediaChildrenInfo(id : Int) : ResultSet? {
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
                    media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                    account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE media_parent = ?
            """.trimIndent(),
            JsonArray().add(id)
    )
}

/**
 * Fetches all data for a media file's children
 * @param id The internal media ID of the media file
 * @return All data for the children of the specified media file
 * @since 1.0
 */
suspend fun fetchMediaChildren(id : Int) : ResultSet? {
    return client?.queryWithParamsAwait(
            """
                SELECT
                    *
                FROM media
                WHERE media_parent = ?
            """.trimIndent(),
            JsonArray().add(id)
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
 * @param excludeTags The tags to exclude when searching
 * @param mime The media MIME pattern (allows % for use as wildcards)
 * @param order The order to return the media files
 * @param offset The offset of rows to return
 * @param limit The amount of rows to return
 * @return The all media file info entries that contain the specified tags
 * @since 1.0
 */
suspend fun fetchMediaListByTags(tags : JsonArray, excludeTags : JsonArray?, mime : String, order : Int, offset : Int, limit : Int) : ResultSet? {
    val params = JsonArray()

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
                    media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                    account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE
                media_parent IS NULL
            """.trimIndent()

    // Add AND statements for tags
    for(tag in tags) {
        sql += "\nAND media_tags::jsonb ?? ?"
        params.add(tag)
    }

    // Add AND NOT statements for excluded tags
    if(excludeTags != null) {
        for(tag in excludeTags) {
            sql += "\nAND NOT media_tags::jsonb ?? ?"
            params.add(tag)
        }
    }

    // Mime type and order
    sql += "\nAND media_mime LIKE ?"
    sql += "\n${ orderBy(order) }"

    // Offset, limit
    sql += "\nOFFSET ? LIMIT ?"

    return client?.queryWithParamsAwait(
            sql,
            params
                    .add(mime)
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Searches media files by plaintext keywords, using PostgreSQL's fulltext search capabilities
 * @param query The plaintext query to search for
 * @param offset The offset of rows to return
 * @param limit The amount of rows to return
 * @param order The order to return the media files
 * @param mime The media MIME pattern (allows % for use as wildcards)
 * @param searchNames Whether to search the names of media files
 * @param searchFilenames Whether to search the filenames of media files
 * @param searchTags Whether to search the tags of media files
 * @param searchDescs Whether to search the descriptions of media files
 * @return All media files matching the specified plaintext query
 * @since 1.0
 */
suspend fun fetchMediaByPlaintextQuery(query : String, offset : Int, limit : Int, order : Int, mime : String, searchNames : Boolean, searchFilenames : Boolean, searchTags : Boolean, searchDescs : Boolean) : ResultSet? {
    val params = JsonArray()
    var searchParts = ArrayList<String>()
    if(searchNames)
        searchParts.add("COALESCE(media_name, '')")
    if(searchFilenames)
        searchParts.add("media_filename")
    if(searchTags)
        searchParts.add("media_tags")
    if(searchDescs)
        searchParts.add("COALESCE(media_description, '')")

    val searchStr = if(searchParts.size > 0)
            """
                (to_tsvector(
                    ${ searchParts.joinToString(" || ' ' || ") }
                ) @@ plainto_tsquery(?) OR
                LOWER(${ searchParts.joinToString(" || ' ' || ") }) LIKE LOWER(?)) AND
            """.trimIndent().also {
                params.add(query)
                params.add("%$query%")
            }
    else
            ""

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
                	media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                	account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE media_parent IS NULL AND
                $searchStr
                media_mime LIKE ?
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            params
                    .add(mime)
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches all media files with the specified tags, and between the provided dates
 * @param tags The tags to search for
 * @param excludeTags The tags to exclude when searching for files
 * @param createdBefore The ISO date String that media files must have been created before (can be null to allow any time)
 * @param createdAfter The ISO date String that media files must have been created after (can be null to allow any time)
 * @param mime The media MIME pattern (allows % for use as wildcards, null to allow all types)
 * @param offset The offset of rows to return
 * @param limit The amount of rows to return
 * @param order The order to return the media files
 * @since 1.0
 */
suspend fun fetchMediaListByTagsAndDateRange(tags : JsonArray?, excludeTags : JsonArray?, createdBefore : String?, createdAfter : String?, mime: String?, offset : Int, limit : Int, order : Int): ResultSet? {
    val params = JsonArray()

    val beforeSql = if(createdBefore == null) {
        ""
    } else {
        params.add(createdBefore)
        "AND media_created_on < ?"
    }
    val afterSql = if(createdAfter == null) {
        ""
    } else {
        params.add(createdAfter)
        "AND media_created_on > ?"
    }
    val mimeSql = if(mime == null) {
        ""
    } else {
        params.add(mime)
        "AND media_mime LIKE ?"
    }

    var tagsSql = ""
    if(tags != null) {
        for(tag in tags) {
            tagsSql += "AND media_tags::jsonb ?? ?"
            params.add(tag)
        }
    }

    var excludeTagsSql = ""
    if(excludeTags != null) {
        for(tag in excludeTags) {
            excludeTagsSql += "AND NOT media_tags::jsonb ?? ?"
            params.add(tag)
        }
    }

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
                	media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                	account_name AS creator_name
                FROM media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE media_parent IS NULL
                AND media_parent IS NULL
                $beforeSql
                $afterSql
                $mimeSql
                $tagsSql
                $excludeTagsSql
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            params
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Fetches a list of media from the specified list
 * @param offset The offset of the media to fetch
 * @param limit The amount of media to return
 * @param list The internal ID of the list to fetch media from
 * @param order The order to return the media files
 * @return All media files in the specified range
 * @since 1.0
 */
suspend fun fetchMediaListByListId(offset : Int, limit : Int, list : Int, order : Int) : ResultSet? {
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
                    media_thumbnail AS thumbnail,
                    media_processing AS processing,
                    media_process_error AS media_process,
                    account_name AS creator_name
                FROM listitems
                LEFT JOIN media ON media.id = item_media
                LEFT JOIN accounts ON accounts.id = media_creator
                WHERE item_list = ?
                ${ orderBy(order) }
                OFFSET ? LIMIT ?
            """.trimIndent(),
            JsonArray()
                    .add(list)
                    .add(offset)
                    .add(limit)
    )
}

/**
 * Updates a media file's info
 * @param id The internal media ID of the media file
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
 * Updates a media file's info
 * @param id The generated alphanumeric ID of the media file
 * @param processing Whether the media file is currently processing
 * @param size The size of the media file in bytes
 * @param hash The hash of the media file
 * @param thumbnailFile The filename of this media file's thumbnail (null if it has none)
 * @param meta The metadata of this media file
 * @since 1.0
 */
suspend fun updateMediaInfo(id : String, processing : Boolean, size : Long, hash : String, thumbnailFile : String?, meta : JsonObject) {
    client?.queryWithParamsAwait(
            """
                UPDATE media
                SET
                    media_processing = ?,
                    media_size = ?,
                    media_file_hash = ?,
                    media_thumbnail_file = ?,
                    media_thumbnail = ?,
                    media_meta = CAST( ? AS jsonb )
                WHERE media_id = ?
            """.trimIndent(),
            JsonArray()
                    .add(processing)
                    .add(size)
                    .add(hash)
                    .add(thumbnailFile)
                    .add(thumbnailFile != null)
                    .add(meta.toString())
                    .add(id)
    )
}

/**
 * Updates a media file entry's processing error field
 * @param id The generated alphanumeric ID of the media file
 * @param error The error to set for this media file (can be null)
 * @since 1.0
 */
suspend fun updateMediaProcessError(id : String, error : String?) {
    client?.queryWithParamsAwait(
            """
                UPDATE media
                SET
                    media_process_error = ?
                WHERE media_id = ?
            """.trimIndent(),
            JsonArray()
                    .add(error)
                    .add(id)
    )
}

/**
 * Deletes a media file entry by its generated alphanumeric ID
 * @param mediaId The generated alphanumeric media ID
 * @since 1.0
 */
suspend fun deleteMedia(mediaId : String) {
    client?.queryWithParamsAwait(
            """
                DELETE FROM media WHERE media_id = ?
            """.trimIndent(),
            JsonArray().add(mediaId)
    )
}