package net.termer.twinemedia.model

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.dataobject.FileRow
import net.termer.twinemedia.dataobject.FileDto
import net.termer.twinemedia.util.toJsonArray
import java.lang.StringBuilder
import java.time.OffsetDateTime

/**
 * Database model for media files
 * @since 1.2.0
 */
class MediaModel(context: Context?, ignoreContext: Boolean): Model(context, ignoreContext) {
	companion object {
		/**
		 * An anonymous [ListsModel] instance that has no context and does not apply any query filters
		 * @since 2.0.0
		 */
		val INSTANCE = ListsModel(null, true)
	}

	/**
	 * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
	 * Order key:
	 * 0 = Creation timestamp, newest to oldest
	 * 1 = Creation timestamp, oldest to newest
	 * 2 = Name alphabetically, ascending
	 * 3 = Name alphabetically, descending
	 * 4 = Size, largest to smallest
	 * 5 = Size, smallest to largest
	 * 6 = Modified date, newest to oldest
	 * 7 = Modified date, oldest to newest
	 * @param order The order
	 * @return The appropriate "ORDER BY" SQL for the selected order
	 */
	private fun orderBy(order: Int): String {
		return "ORDER BY " + when(order) {
			1 -> "file_created_ts ASC"
			2 -> "file_name ASC, file_filename ASC"
			3 -> "file_name DESC, file_filename DESC"
			4 -> "file_size DESC"
			5 -> "file_size ASC"
			6 -> "file_modified_on DESC"
			7 -> "file_modified_on ASC"
			else -> "file_created_ts DESC"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for listing files based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun listWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherFiles -> "file_creator = ${context!!.account.id}"
			context!!.account.hasPermission("files.list.all") -> "TRUE"
			else -> "file_creator = ${context!!.account.id}"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for fetching single files based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun viewWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherFiles -> "file_creator = ${context!!.account.id}"
			context!!.account.hasPermission("files.view.all") -> "TRUE"
			else -> "file_creator = ${context!!.account.id}"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for fetching single files for editing based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun editWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherFiles -> "file_creator = ${context!!.account.id}"
			context!!.account.hasPermission("files.edit.all") -> "TRUE"
			else -> "file_creator = ${context!!.account.id}"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for fetching single files for deleting based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 */
	private fun deleteWhereFilter(): String {
		return when {
			ignoreContext -> "TRUE"
			context == null -> "FALSE"
			context!!.account.excludeOtherFiles -> "file_creator = ${context!!.account.id}"
			context!!.account.hasPermission("files.delete.all") -> "TRUE"
			else -> "file_creator = ${context!!.account.id}"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for excluding tag IDs based on the user object associated with this model
	 * @param params The map of SQL parameters to use for escaping values
	 * @return A default SQL WHERE filter to insert into a query
	 */
	private fun excludeTagsFilter(params: HashMap<String, Any?>): String {
		val sql = StringBuilder("TRUE")

		// TODO Rework this to search the file_tags table (may need some joining or something, not sure)
		if(context != null) {
			for((index, tag) in context!!.account.excludeTags.withIndex()) {
				sql.append(" AND NOT file_tag_ids::jsonb ? #{prefExcludeTag$index}")
				params["prefExcludeTag$index"] = tag
			}
		}

		return sql.toString()
	}

	/**
	 * SELECT statement for getting info
	 * @param extra Extra rows to select (can be null for none)
	 * @return The SELECT statement
	 */
	private fun infoSelect(extra: String?): String {
		return """
			SELECT
				file.id AS internal_id,
				file_parent AS parent_internal_id,
				file_id AS id,
				file_name AS name,
				file_filename AS filename,
				file_size AS size,
				file_mime AS mime,
				file_creator AS creator,
				file_hash AS hash,
				(file_thumbnail_key IS NULL) AS has_thumbnail,
				file_processing AS processing,
				file_process_error AS process_error,
				file_source AS source,
				account_name AS creator_name,
				source_type,
				source_name,
				file_created_ts AS created_ts,
				file_modified_ts AS modified_ts
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM files
			LEFT JOIN accounts ON accounts.id = file_creator
			LEFT JOIN sources ON sources.id = file_source
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
	 * @param key The media source key to use for accessing the underlying media file
	 * @param creator The ID of the account that uploaded this media file
	 * @param hash The underlying media file's hash
	 * @param thumbnailFile The filename of the media's generated thumbnail, null if none
	 * @param meta Any metadata for the file stored as JSON
	 * @param sourceId The ID of the source this media file is stored on
	 * @return The newly created media entry's ID
	 * @since 1.5.0
	 */
	suspend fun createMedia(id: String, name: String?, filename: String, description: String?, tags: Array<String>, size: Long, mime: String, key: String, creator: Int, hash: String, thumbnailFile: String?, meta: JsonObject, sourceId: Int): Int {
		return SqlTemplate
				.forQuery(client, """
					INSERT INTO files
					( file_id, file_name, file_filename, file_description, file_tags, file_size, file_mime, file_key, file_creator, file_hash, file_thumbnail, file_thumbnail_file, file_meta, file_source )
					VALUES
					( #{id}, #{name}, #{filename}, #{desc}, CAST( #{tags} AS jsonb ), #{size}, #{mime}, #{key}, #{creator}, #{hash}, #{thumbnail}, #{thumbnailFile}, CAST( #{meta} AS jsonb ), #{source} )
					RETURNING id
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"name" to name,
						"filename" to filename,
						"desc" to description,
						"tags" to tags.toJsonArray(),
						"size" to size,
						"mime" to mime,
						"key" to key,
						"creator" to creator,
						"hash" to hash,
						"thumbnail" to (thumbnailFile != null),
						"thumbnailFile" to thumbnailFile,
						"meta" to meta,
						"source" to sourceId
				)).await()
				.first().getInteger("id")
	}

	/**
	 * Creates a new media entry
	 * @param id The generated alphanumeric media ID
	 * @param name The media file's name (can be null)
	 * @param filename The media's original filename
	 * @param size The size of the media (in bytes)
	 * @param mime The mime type of the media
	 * @param key The media source key to use for accessing the underlying media file
	 * @param creator The ID of the account that uploaded this media file
	 * @param hash The underlying media file's hash
	 * @param thumbnailFile The filename of the media's generated thumbnail, null if none
	 * @param meta Any metadata for the file stored as JSON
	 * @param parent The parent of this media file
	 * @param processing Whether the media file is processing
	 * @param sourceId The ID of the source this media file is stored on
	 * @return The newly created media entry's ID
	 * @since 1.5.0
	 */
	suspend fun createMedia(id: String, name: String?, filename: String, size: Long, mime: String, key: String, creator: Int, hash: String, thumbnailFile: String?, meta: JsonObject, parent: Int, processing: Boolean, sourceId: Int): Int {
		return SqlTemplate
				.forQuery(client, """
					INSERT INTO files
					( file_id, file_name, file_filename, file_size, file_mime, file_key, file_creator, file_hash, file_thumbnail, file_thumbnail_file, file_meta, file_parent, file_processing, file_source )
					VALUES
					( #{id}, #{name}, #{filename}, #{size}, #{mime}, #{key}, #{creator}, #{hash}, #{thumbnail}, #{thumbnailFile}, CAST( #{meta} AS jsonb ), #{parent}, #{processing}, #{source} )
					RETURNING id
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"name" to name,
						"filename" to filename,
						"size" to size,
						"mime" to mime,
						"key" to key,
						"creator" to creator,
						"hash" to hash,
						"thumbnail" to (thumbnailFile != null),
						"thumbnailFile" to thumbnailFile,
						"meta" to meta,
						"parent" to parent,
						"processing" to processing,
						"source" to sourceId
				)).await()
				.first().getInteger("id")
	}

	/**
	 * Fetches the media entry with the specified generated ID
	 * @param mediaId The generated alphanumeric media ID to search for
	 * @return The specified media file
	 * @since 1.4.0
	 */
	suspend fun fetchMedia(mediaId: String): RowSet<FileRow> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM files WHERE ${viewWhereFilter()} AND file_id = #{mediaId}
				""".trimIndent())
				.mapTo(FileRow.MAPPER)
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
	suspend fun fetchMediaList(offset: Int, limit: Int, mime: String, order: Int): RowSet<FileDto> {
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
					AND file_parent IS NULL
					AND file_mime LIKE #{mime}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(FileDto.MAPPER)
				.execute(params).await()
	}

	/**
	 * Fetches info about a media file. Note: the returned "internal_id" field should be removed before serving info the the client.
	 * @param mediaId The alphanumeric generated file ID to search
	 * @return The info for the specified media file
	 * @since 1.4.0
	 */
	suspend fun fetchMediaInfo(mediaId: String): RowSet<FileDto> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect("file_description AS description")}
					WHERE
					${viewWhereFilter()}
					AND file_id = #{mediaId}
				""".trimIndent())
				.mapTo(FileDto.MAPPER)
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
	suspend fun fetchMediaInfo(id: Int): RowSet<FileDto> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect("file_description AS description")}
					WHERE
					${viewWhereFilter()}
					AND media.id = #{id}
				""".trimIndent())
				.mapTo(FileDto.MAPPER)
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
	suspend fun fetchMediaChildrenInfo(id: Int): RowSet<FileDto> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${viewWhereFilter()}
					AND file_parent = #{id}
				""".trimIndent())
				.mapTo(FileDto.MAPPER)
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
	suspend fun fetchMediaChildren(id: Int): RowSet<FileRow> {
		return SqlTemplate
				.forQuery(client, """
					SELECT
						*
					FROM files
					WHERE
					${viewWhereFilter()}
					AND file_parent = #{id}
				""".trimIndent())
				.mapTo(FileRow.MAPPER)
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
	suspend fun fetchMediaByHash(hash: String): RowSet<FileRow> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM files
					WHERE
					${listWhereFilter()}
					AND file_hash = #{hash}
				""".trimIndent())
				.mapTo(FileRow.MAPPER)
				.execute(hashMapOf<String, Any>(
						"hash" to hash
				)).await()
	}

	/**
	 * Fetches all media files with the specified hash in the provided media source
	 * @param hash The file hash to search for
	 * @param source The ID of the source to check in
	 * @since 1.5.0
	 */
	suspend fun fetchMediaByHashAndSource(hash: String, source: Int): RowSet<FileRow> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM files
					WHERE
					${listWhereFilter()}
					AND file_hash = #{hash}
					AND file_source = #{source}
				""".trimIndent())
				.mapTo(FileRow.MAPPER)
				.execute(hashMapOf<String, Any>(
						"hash" to hash,
						"source" to source
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
	suspend fun fetchMediaListByTags(tags: Array<String>, excludeTags: Array<String>?, mime: String, order: Int, offset: Int, limit: Int): RowSet<FileDto> {
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
				AND file_parent IS NULL
			""".trimIndent()

		// Add AND statements for tags
		for((index, tag) in tags.withIndex()) {
			sql += "\nAND file_tags::jsonb ? LOWER(#{tag$index})"
			params["tag$index"] = tag
		}

		// Add AND NOT statements for excluded tags
		for((index, tag) in excludeTags.orEmpty().withIndex()) {
			sql += "\nAND NOT file_tags::jsonb ? LOWER(#{excludeTag$index})"
			params["excludeTag$index"] = tag
		}

		// Mime type and order
		sql += "\nAND file_mime LIKE #{mime}"
		sql += "\n${orderBy(order)}"

		// Offset, limit
		sql += "\nOFFSET #{offset} LIMIT #{limit}"

		return SqlTemplate
				.forQuery(client, sql)
				.mapTo(FileDto.MAPPER)
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
	suspend fun fetchMediaByPlaintextQuery(query: String, offset: Int, limit: Int, order: Int, mime: String, searchNames: Boolean, searchFilenames: Boolean, searchTags: Boolean, searchDescs: Boolean): RowSet<FileDto> {
		val params = hashMapOf<String, Any?>(
				"mime" to mime,
				"offset" to offset,
				"limit" to limit,
				"queryRaw" to query,
				"query" to "%$query%"
		)

		val searchParts = ArrayList<String>()
		if(searchNames)
			searchParts.add("COALESCE(file_name, '')")
		if(searchFilenames)
			searchParts.add("file_filename")
		if(searchTags)
			searchParts.add("file_tags")
		if(searchDescs)
			searchParts.add("COALESCE(file_description, '')")

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
					AND file_parent IS NULL AND
					$searchStr
					${excludeTagsFilter(params)}
					AND file_mime LIKE #{mime}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(FileDto.MAPPER)
				.execute(params).await()
	}

	/**
	 * Fetches all media files with the specified tags, between the provided dates, and the media creator
	 * @param tags The tags to search for
	 * @param excludeTags The tags to exclude when searching for files
	 * @param createdBefore The time that media files must have been created before (can be null to allow any time)
	 * @param createdAfter The time that media files must have been created after (can be null to allow any time)
	 * @param mime The media MIME pattern (allows % for use as wildcards, null to allow all types)
	 * @param creator The account which files must be created by (specifying null will return files from all accounts)
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the media files
	 * @since 1.4.2
	 */
	suspend fun fetchMediaListByTagsDateRangeAndCreator(tags: Array<String>?, excludeTags: Array<String>?, createdBefore: OffsetDateTime?, createdAfter: OffsetDateTime?, mime: String?, creator: Int?, offset: Int, limit: Int, order: Int): RowSet<FileDto> {
		val params = hashMapOf<String, Any?>(
				"createdBefore" to createdBefore,
				"createdAfter" to createdAfter,
				"mime" to mime,
				"offset" to offset,
				"limit" to limit,
				"creator" to creator
		)

		val beforeSql = if(createdBefore == null)
			""
		else
			"AND file_created_on < #{createdBefore}"

		val afterSql = if(createdAfter == null)
			""
		else
			"AND file_created_on > #{createdAfter}"

		val mimeSql = if(mime == null)
			""
		else
			"AND file_mime LIKE #{mime}"

		val creatorSql = if(creator == null)
			""
		else
			"AND file_creator = #{creator}"

		var tagsSql = ""
		if(tags != null) {
			for((index, tag) in tags.withIndex()) {
				tagsSql += "AND file_tags::jsonb ? LOWER(#{tag$index})"
				params["tag$index"] = tag
			}
		}

		var excludeTagsSql = ""
		if(excludeTags != null) {
			for((index, tag) in excludeTags.withIndex()) {
				excludeTagsSql += "AND NOT file_tags::jsonb ? LOWER(#{excludeTag$index})"
				params["excludeTag$index"] = tag
			}
		}

		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE
					${listWhereFilter()}
					AND file_parent IS NULL
					$beforeSql
					$afterSql
					$mimeSql
					$creatorSql
					$tagsSql
					$excludeTagsSql AND
					${excludeTagsFilter(params)}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(FileDto.MAPPER)
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
	suspend fun fetchMediaListByListId(offset: Int, limit: Int, list: Int, order: Int): RowSet<FileDto> {
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
				.mapTo(FileDto.MAPPER)
				.execute(params).await()
	}

	/**
	 * Fetches all media files with the specified media source
	 * @param source The source's ID
	 * @param offset The offset of the media to fetch
	 * @param limit The amount of media to return
	 * @param order The order to return the media files
	 * @since 1.5.0
	 */
	suspend fun fetchMediaBySource(source: Int, offset: Int, limit: Int, order: Int): RowSet<FileRow> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM files
					WHERE
					${listWhereFilter()}
					AND file_source = #{source}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(FileRow.MAPPER)
				.execute(hashMapOf<String, Any>(
						"source" to source,
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches the amount of media entries with the specified source
	 * @param sourceId The source's ID
	 * @return The amount of media entries with the specified source
	 * @since 1.5.0
	 */
	suspend fun fetchMediaCountBySource(sourceId: Int): Int {
		return SqlTemplate
				.forQuery(client, """
					SELECT COUNT(*) FROM files
					WHERE ${listWhereFilter()}
					AND file_source = #{source}
				""".trimIndent())
				.execute(mapOf<String, Any>(
						"source" to sourceId
				)).await()
				.first().getInteger("count")
	}

	/**
	 * Updates a media file's info
	 * @param id The internal media ID of the media file
	 * @param newName The new name for this media file
	 * @param newDesc The new description for this media file
	 * @param newTags The new tags for this media
	 * @param creator The new creator of this media
	 * @since 1.5.0
	 */
	suspend fun updateMediaInfo(id: Int, newFilename: String, newName: String?, newDesc: String?, newTags: Array<String>, creator: Int) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE media
					SET
						file_filename = #{filename},
						file_name = #{name},
						file_description = #{desc},
						file_tags = CAST( #{tags} AS jsonb ),
						file_creator = #{creator},
						file_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"filename" to newFilename,
						"name" to newName,
						"desc" to newDesc,
						"tags" to newTags.toJsonArray(),
						"creator" to creator
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
						file_processing = #{processing},
						file_size = #{size},
						file_hash = #{hash},
						file_thumbnail_file = #{thumbnailFile},
						file_thumbnail = #{thumbnail},
						file_meta = CAST( #{meta} AS jsonb ),
						file_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND file_id = #{id}
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
						file_process_error = #{error},
						file_modified_on = NOW()
					WHERE
					${editWhereFilter()}
					AND file_id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"error" to error
				)).await()
	}

	/**
	 * Updates the source of all media files with the specified source
	 * @param oldSource The old source ID
	 * @param newSource The new source ID
	 * @since 1.5.0
	 */
	suspend fun updateMediaSourceBySource(oldSource: Int, newSource: Int) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE media
					SET
						file_source = #{new}
					WHERE
					${editWhereFilter()}
					AND file_source = #{old}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"old" to oldSource,
						"new" to newSource
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
					DELETE FROM files WHERE ${deleteWhereFilter()} AND file_id = #{mediaId}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"mediaId" to mediaId
				)).await()
	}

	/**
	 * Deletes all media file entries with the specified source
	 * @param sourceId The source ID
	 * @since 1.5.0
	 */
	suspend fun deleteMediaBySource(sourceId: Int) {
		SqlTemplate
				.forUpdate(client, """
					DELETE FROM files WHERE ${deleteWhereFilter()} AND file_source = #{source}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"source" to sourceId
				)).await()
	}
}