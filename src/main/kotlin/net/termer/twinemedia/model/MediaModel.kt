package net.termer.twinemedia.model

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.db.dataobject.Account
import net.termer.twinemedia.db.dataobject.Media
import net.termer.twinemedia.db.dataobject.MediaInfo
import net.termer.twinemedia.util.toJsonArray
import java.lang.StringBuilder
import java.time.OffsetDateTime

/**
 * Database model for media files
 * @since 1.2.0
 */
class MediaModel {
	private var _account: Account? = null

	/**
	 * The account associated with this model instance
	 * @since 1.2.0
	 */
	var account: Account?
		get() = _account
		set(value) { _account = value }

	/**
	 * Creates a new MediaModel with an account
	 * @param account The account to use for this model instance
	 * @since 1.2.0
	 */
	constructor(account: Account) {
		this.account = account
	}
	/**
	 * Creates a new MediaModel without an account
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
	 * 6 = Modified date, newest to oldest
	 * 7 = Modified date, oldest to newest
	 * @param order The order
	 * @return The appropriate "ORDER BY" SQL for the selected order
	 * @since 1.0.0
	 */
	private fun orderBy(order: Int): String {
		return "ORDER BY " + when (order) {
			1 -> "media_created_on ASC"
			2 -> "media_name ASC, media_filename ASC"
			3 -> "media_name DESC, media_filename DESC"
			4 -> "media_size DESC"
			5 -> "media_size ASC"
			6 -> "media_modified_on DESC"
			7 -> "media_modified_on ASC"
			else -> "media_created_on DESC"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for listing files based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun listWhereFilter(): String {
		return when {
			account == null -> {
				"TRUE"
			}
			account!!.excludeOtherMedia -> {
				"media_creator = ${account?.id}"
			}
			account!!.hasPermission("files.list.all") -> {
				"TRUE"
			}
			else -> {
				"media_creator = ${account?.id}"
			}
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single files based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun viewWhereFilter(): String {
		return when {
			account == null -> {
				"TRUE"
			}
			account!!.excludeOtherMedia -> {
				"media_creator = ${account?.id}"
			}
			account!!.hasPermission("files.view.all") -> {
				"TRUE"
			}
			else -> {
				"media_creator = ${account?.id}"
			}
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single files for editing based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun editWhereFilter(): String {
		return when {
			account == null -> {
				"TRUE"
			}
			account!!.excludeOtherMedia -> {
				"media_creator = ${account?.id}"
			}
			account!!.hasPermission("files.edit.all") -> {
				"TRUE"
			}
			else -> {
				"media_creator = ${account?.id}"
			}
		}
	}
	/**
	 * Applies a default SQL WHERE filter for fetching single files for deleting based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.2.0
	 */
	private fun deleteWhereFilter(): String {
		return when {
			account == null -> {
				"TRUE"
			}
			account!!.excludeOtherMedia -> {
				"media_creator = ${account?.id}"
			}
			account!!.hasPermission("files.delete.all") -> {
				"TRUE"
			}
			else -> {
				"media_creator = ${account?.id}"
			}
		}
	}

	/**
	 * Applies a default SQL WHERE filter for excluding tags based on the user object associated with this model
	 * @param params The map of SQL parameters to use for escaping values
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.4.0
	 */
	private fun excludeTagsFilter(params: HashMap<String, Any?>): String {
		val sql = StringBuilder("TRUE")

		if(account != null) {
			for((index, tag) in account!!.excludeTags.withIndex()) {
				sql.append(" AND NOT media_tags::jsonb ? #{prefExcludeTag$index}")
				params["prefExcludeTag$index"] = tag
			}
		}

		return sql.toString()
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
				media.id AS internal_id,
				media_parent AS internal_parent,
				media_id AS id,
				media_name AS name,
				media_filename AS filename,
				media_size AS size,
				media_mime AS mime,
				media_tags AS tags,
				media_created_on AS created_on,
				media_modified_on AS modified_on,
				media_creator AS creator,
				media_file_hash AS file_hash,
				media_thumbnail AS thumbnail,
				media_processing AS processing,
				media_process_error AS process_error,
				account_name AS creator_name
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM media
			LEFT JOIN accounts ON accounts.id = media_creator
		""".trimIndent()
	}
	/**
	 * SELECT statement for getting info
	 * @return The SELECT statement
	 * @since 1.4.0
	 */
	private fun infoSelect() = infoSelect(null)

	/**
	 * Creates a new media entry
	 * @param id The generated alphanumeric media ID
	 * @param name The name of the file (can be null)
	 * @param filename The media's original filename
	 * @param description The description for the entry (can be null)
	 * @param tags The tags for the entry
	 * @param size The size of the media (in bytes)
	 * @param mime The mime type of the media
	 * @param file The name of the saved media file
	 * @param creator The ID of the account that uploaded this media file
	 * @param thumbnailFile The filename of the media's generated thumbnail, null if none
	 * @param meta Any metadata for the file stored as JSON
	 * @since 1.4.0
	 */
	suspend fun createMedia(id: String, name: String?, filename: String, description: String?, tags: Array<String>, size: Long, mime: String, file: String, creator: Int, hash: String, thumbnailFile: String?, meta: JsonObject) {
		SqlTemplate
				.forUpdate(client, """
					INSERT INTO media
					( media_id, media_name, media_filename, media_description, media_tags, media_size, media_mime, media_file, media_creator, media_file_hash, media_thumbnail, media_thumbnail_file, media_meta )
					VALUES
					( #{id}, #{name}, #{filename}, #{desc}, CAST( #{tags} AS jsonb ), #{size}, #{mime}, #{file}, #{creator}, #{hash}, #{thumbnail}, #{thumbnailFile}, CAST( #{meta} AS jsonb ) )
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"name" to name,
						"filename" to filename,
						"desc" to description,
						"tags" to tags.toJsonArray(),
						"size" to size,
						"mime" to mime,
						"file" to file,
						"creator" to creator,
						"hash" to hash,
						"thumbnail" to (thumbnailFile != null),
						"thumbnailFile" to thumbnailFile,
						"meta" to meta
				)).await()
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
	 * @param meta Any metadata for the file stored as JSON
	 * @param parent The parent of this media file
	 * @param processing Whether the media file is processing
	 * @since 1.0.0
	 */
	suspend fun createMedia(id: String, name: String?, filename: String, size: Long, mime: String, file: String, creator: Int, hash: String, thumbnailFile: String?, meta: JsonObject, parent: Int, processing: Boolean) {
		SqlTemplate
				.forUpdate(client, """
					INSERT INTO media
					( media_id, media_name, media_filename, media_size, media_mime, media_file, media_creator, media_file_hash, media_thumbnail, media_thumbnail_file, media_meta, media_parent, media_processing )
					VALUES
					( #{id}, #{name}, #{filename}, #{size}, #{mime}, #{file}, #{creator}, #{hash}, #{thumbnail}, #{thumbnailFile}, CAST( #{meta} AS jsonb ), #{parent}, #{processing} )
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"name" to name,
						"filename" to filename,
						"size" to size,
						"mime" to mime,
						"file" to file,
						"creator" to creator,
						"hash" to hash,
						"thumbnail" to (thumbnailFile != null),
						"thumbnailFile" to thumbnailFile,
						"meta" to meta,
						"parent" to parent,
						"processing" to processing
				)).await()
	}

	/**
	 * Fetches the media entry with the specified generated ID
	 * @param mediaId The generated alphanumeric media ID to search for
	 * @return All media files from database search
	 * @since 1.4.0
	 */
	suspend fun fetchMedia(mediaId: String): RowSet<Media> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM media WHERE ${viewWhereFilter()} AND media_id = #{mediaId}
				""".trimIndent())
				.mapTo(Media.MAPPER)
				.execute(hashMapOf<String, Any>(
						"mediaId" to mediaId
				)).await()
	}

	/**
	 * Fetches a list of media
	 * @param offset The offset of the media to fetch
	 * @param limit The amount of media to return
	 * @param mime The media MIME pattern (allows % for use as wildcards)
	 * @param order The order to return the media files
	 * @return All media files in the specified range
	 * @since 1.4.0
	 */
	suspend fun fetchMediaList(offset: Int, limit: Int, mime: String, order: Int): RowSet<MediaInfo> {
		val params = hashMapOf<String, Any?>(
				"offset" to offset,
				"limit" to limit,
				"mime" to mime
		)

		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${listWhereFilter()}
					AND ${excludeTagsFilter(params)}
					AND media_parent IS NULL
					AND media_mime LIKE #{mime}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(params).await()
	}

	/**
	 * Fetches info about a media file. Note: the returned "internal_id" field should be removed before serving info the the client.
	 * @param mediaId The alphanumeric generated file ID to search
	 * @return The info for the specified media file
	 * @since 1.4.0
	 */
	suspend fun fetchMediaInfo(mediaId: String): RowSet<MediaInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${viewWhereFilter()}
					AND media_id = #{mediaId}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"mediaId" to mediaId
				)).await()
	}

	/**
	 * Fetches info about a media file. Note: the returned "internal_id" field should be removed before serving info the the client.
	 * @param id The media file's internal ID
	 * @return The info for the specified media file
	 * @since 1.4.0
	 */
	suspend fun fetchMediaInfo(id: Int): RowSet<MediaInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${viewWhereFilter()}
					AND media.id = #{id}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches info for a media file's children
	 * @param id The internal media ID of the media file to fetch children of
	 * @return All info for the children of the specified media file
	 * @since 1.4.0
	 */
	suspend fun fetchMediaChildrenInfo(id: Int): RowSet<MediaInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${viewWhereFilter()}
					AND media_parent = #{id}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches all data for a media file's children
	 * @param id The internal media ID of the media file to fetch children of
	 * @return All data for the children of the specified media file
	 * @since 1.4.0
	 */
	suspend fun fetchMediaChildren(id: Int): RowSet<Media> {
		return SqlTemplate
				.forQuery(client, """
					SELECT
						*
					FROM media
					WHERE
					${viewWhereFilter()}
					AND media_parent = #{id}
				""".trimIndent())
				.mapTo(Media.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches all media files with the provided hash
	 * @param hash The file hash to search for
	 * @return All media files with the specified hash
	 * @since 1.4.0
	 */
	suspend fun fetchMediaByHash(hash: String): RowSet<Media> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM media
					WHERE
					${listWhereFilter()}
					AND media_file_hash = #{hash}
				""".trimIndent())
				.mapTo(Media.MAPPER)
				.execute(hashMapOf<String, Any>(
						"hash" to hash
				)).await()
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
	 * @since 1.4.0
	 */
	suspend fun fetchMediaListByTags(tags: Array<String>, excludeTags: Array<String>?, mime: String, order: Int, offset: Int, limit: Int): RowSet<MediaInfo> {
		val params = hashMapOf<String, Any?>(
				"mime" to mime,
				"offset" to offset,
				"limit" to limit
		)

		var sql = """
				${infoSelect()}
				WHERE
				${listWhereFilter()}
				AND ${excludeTagsFilter(params)}
				AND media_parent IS NULL
			""".trimIndent()

		// Add AND statements for tags
		for((index, tag) in tags.withIndex()) {
			sql += "\nAND media_tags::jsonb ? #{tag$index}"
			params["tag$index"] = tag
		}

		// Add AND NOT statements for excluded tags
		for((index, tag) in excludeTags.orEmpty().withIndex()) {
			sql += "\nAND NOT media_tags::jsonb ? #{excludeTag$index}"
			params["excludeTag$index"] = tag
		}

		// Mime type and order
		sql += "\nAND media_mime LIKE #{mime}"
		sql += "\n${orderBy(order)}"

		// Offset, limit
		sql += "\nOFFSET #{offset} LIMIT #{limit}"

		return SqlTemplate
				.forQuery(client, sql)
				.mapTo(MediaInfo.MAPPER)
				.execute(params).await()
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
	 * @since 1.4.0
	 */
	suspend fun fetchMediaByPlaintextQuery(query: String, offset: Int, limit: Int, order: Int, mime: String, searchNames: Boolean, searchFilenames: Boolean, searchTags: Boolean, searchDescs: Boolean): RowSet<MediaInfo> {
		val params = hashMapOf<String, Any?>(
				"mime" to mime,
				"offset" to offset,
				"limit" to limit,
				"queryRaw" to query,
				"query" to "%$query%"
		)

		val searchParts = ArrayList<String>()
		if(searchNames)
			searchParts.add("COALESCE(media_name, '')")
		if(searchFilenames)
			searchParts.add("media_filename")
		if(searchTags)
			searchParts.add("media_tags")
		if(searchDescs)
			searchParts.add("COALESCE(media_description, '')")

		val searchStr = if(searchParts.size > 0 && query.isNotBlank())
			"""
				(to_tsvector(
					${searchParts.joinToString(" || ' ' || ")}
				) @@ plainto_tsquery(#{queryRaw}) OR
				LOWER(${searchParts.joinToString(" || ' ' || ")}) LIKE LOWER(#{query})) AND
			"""
		else
			""

		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${listWhereFilter()}
					AND media_parent IS NULL AND
					$searchStr
					${excludeTagsFilter(params)}
					AND media_mime LIKE #{mime}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(params).await()
	}

	/**
	 * Fetches all media files with the specified tags, and between the provided dates
	 * @param tags The tags to search for
	 * @param excludeTags The tags to exclude when searching for files
	 * @param createdBefore The time that media files must have been created before (can be null to allow any time)
	 * @param createdAfter The time that media files must have been created after (can be null to allow any time)
	 * @param mime The media MIME pattern (allows % for use as wildcards, null to allow all types)
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the media files
	 * @since 1.4.0
	 */
	suspend fun fetchMediaListByTagsAndDateRange(tags: Array<String>?, excludeTags: Array<String>?, createdBefore: OffsetDateTime?, createdAfter: OffsetDateTime?, mime: String?, offset: Int, limit: Int, order: Int): RowSet<MediaInfo> {
		val params = hashMapOf(
				"createdBefore" to createdBefore,
				"createdAfter" to createdAfter,
				"mime" to mime,
				"offset" to offset,
				"limit" to limit
		)

		val beforeSql = if(createdBefore == null)
			""
		else
			"AND media_created_on < #{createdBefore}"

		val afterSql = if(createdAfter == null)
			""
		else
			"AND media_created_on > #{createdAfter}"

		val mimeSql = if(mime == null)
			""
		else
			"AND media_mime LIKE #{mime}"

		var tagsSql = ""
		if(tags != null) {
			for((index, tag) in tags.withIndex()) {
				tagsSql += "AND media_tags::jsonb ? #{tag$index}"
				params["tag$index"] = tag
			}
		}

		var excludeTagsSql = ""
		if(excludeTags != null) {
			for((index, tag) in excludeTags.withIndex()) {
				excludeTagsSql += "AND NOT media_tags::jsonb ? #{excludeTag$index}"
				params["excludeTag$index"] = tag
			}
		}

		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${listWhereFilter()}
					AND media_parent IS NULL
					$beforeSql
					$afterSql
					$mimeSql
					$tagsSql
					$excludeTagsSql AND
					${excludeTagsFilter(params)}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(params).await()
	}

	/**
	 * Fetches a list of media from the specified list
	 * @param offset The offset of the media to fetch
	 * @param limit The amount of media to return
	 * @param list The internal ID of the list to fetch media from
	 * @param order The order to return the media files
	 * @return All media files in the specified range
	 * @since 1.4.0
	 */
	suspend fun fetchMediaListByListId(offset: Int, limit: Int, list: Int, order: Int): RowSet<MediaInfo> {
		val params = hashMapOf<String, Any?>(
				"offset" to offset,
				"limit" to limit,
				"list" to list
		)

		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					LEFT JOIN listitems ON item_media = media.id
					WHERE
					${listWhereFilter()}
					AND ${excludeTagsFilter(params)}
					AND item_list = #{list}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(MediaInfo.MAPPER)
				.execute(params).await()
	}

	/**
	 * Updates a media file's info
	 * @param id The internal media ID of the media file
	 * @param newName The new name for this media file
	 * @param newDesc The new description for this media file
	 * @param newTags The new tags for this media
	 * @since 1.4.0
	 */
	suspend fun updateMediaInfo(id: Int, newFilename: String, newName: String?, newDesc: String?, newTags: Array<String>) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE media
					SET
						media_filename = #{filename},
						media_name = #{name},
						media_description = #{desc},
						media_tags = CAST( #{tags} AS jsonb ),
						media_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"filename" to newFilename,
						"name" to newName,
						"desc" to newDesc,
						"tags" to newTags.toJsonArray()
				)).await()
	}

	/**
	 * Updates a media file's info
	 * @param id The generated alphanumeric ID of the media file
	 * @param processing Whether the media file is currently processing
	 * @param size The size of the media file in bytes
	 * @param hash The hash of the media file
	 * @param thumbnailFile The filename of this media file's thumbnail (null if it has none)
	 * @param meta The metadata of this media file
	 * @since 1.0.0
	 */
	suspend fun updateMediaInfo(id: String, processing: Boolean, size: Long, hash: String, thumbnailFile: String?, meta: JsonObject) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE media
					SET
						media_processing = #{processing},
						media_size = #{size},
						media_file_hash = #{hash},
						media_thumbnail_file = #{thumbnailFile},
						media_thumbnail = #{thumbnail},
						media_meta = CAST( #{meta} AS jsonb ),
						media_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND media_id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"processing" to processing,
						"size" to size,
						"hash" to hash,
						"thumbnailFile" to thumbnailFile,
						"thumbnail" to (thumbnailFile != null),
						"meta" to meta
				)).await()
	}

	/**
	 * Updates a media file entry's processing error field
	 * @param id The generated alphanumeric ID of the media file
	 * @param error The error to set for this media file (can be null)
	 * @since 1.0.0
	 */
	suspend fun updateMediaProcessError(id: String, error: String?) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE media
					SET
						media_process_error = #{error},
						media_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND media_id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"error" to error
				)).await()
	}

	/**
	 * Deletes a media file entry by its generated alphanumeric ID
	 * @param mediaId The generated alphanumeric media ID
	 * @since 1.0.0
	 */
	suspend fun deleteMedia(mediaId: String) {
		SqlTemplate
				.forUpdate(client, """
					DELETE FROM media WHERE ${deleteWhereFilter()} AND media_id = #{mediaId}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"mediaId" to mediaId
				)).await()
	}
}