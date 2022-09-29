package net.termer.twinemedia.model

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.templates.SqlTemplate
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.dataobject.Source
import net.termer.twinemedia.dataobject.SourceInfo
import net.termer.twinemedia.db.Database.client
import net.termer.twinemedia.source.FileSourceManager

/**
 * Database model for sources
 * @since 1.5.0
 */
class SourcesModel {
	private var _account: AccountRow? = null

	/**
	 * The account associated with this model instance
	 * @since 1.5.0
	 */
	var account: AccountRow?
		get() = _account
		set(value) { _account = value }

	/**
	 * Creates a new SourcesModel with an account
	 * @param account The account to use for this model instance
	 * @since 1.5.0
	 */
	constructor(account: AccountRow) {
		this.account = account
	}
	/**
	 * Creates a new SourcesModel without an account
	 * @since 1.5.0
	 */
	constructor() {
		this.account = null
	}

	/**
	 * Applies a default SQL WHERE filter for fetching single sources based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.5.0
	 */
	private fun viewWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherSources -> "source_creator = ${account?.id}"
			account!!.hasPermission("sources.view.all") -> "TRUE"
			else -> "source_creator = ${account?.id} OR source_global = TRUE"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for listing sources based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.5.0
	 */
	private fun listWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherSources -> "source_creator = ${account?.id}"
			account!!.hasPermission("sources.list.all") -> "TRUE"
			else -> "source_creator = ${account?.id} OR source_global = TRUE"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for editing sources based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.5.0
	 */
	private fun editWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherSources -> "source_creator = ${account?.id}"
			account!!.hasPermission("sources.edit.all") -> "TRUE"
			else -> "source_creator = ${account?.id}"
		}
	}

	/**
	 * Applies a default SQL WHERE filter for deleting sources based on the user object associated with this model
	 * @return a default SQL WHERE filter to insert into a query
	 * @since 1.5.0
	 */
	private fun deleteWhereFilter(): String {
		return when {
			account == null -> "TRUE"
			account!!.excludeOtherSources -> "source_creator = ${account?.id}"
			account!!.hasPermission("sources.delete.all") -> "TRUE"
			else -> "source_creator = ${account?.id}"
		}
	}

	/**
	 * Utility function to generate an ORDER BY statement based on an integer corresponding to a combination of criteria and order.
	 * Order key:
	 * 0 = Creation date, newest to oldest
	 * 1 = Creation date, oldest to newest
	 * 2 = Name alphabetically, ascending
	 * 3 = Name alphabetically, descending
	 * 4 = Type alphabetically, ascending
	 * 5 = Type alphabetically, descending
	 * @param order The order
	 * @return The appropriate "ORDER BY" SQL for the selected order
	 * @since 1.5.0
	 */
	private fun orderBy(order: Int): String {
		return "ORDER BY " + when (order) {
			1 -> "source_created_on ASC"
			2 -> "source_name ASC"
			3 -> "source_name DESC"
			4 -> "source_type ASC"
			5 -> "source_type DESC"
			else -> "source_created_on DESC"
		}
	}

	/**
	 * SELECT statement for getting info
	 * @param extra Extra rows to select (can be null for none)
	 * @return The SELECT statement
	 * @since 1.5.0
	 */
	private fun infoSelect(extra: String?): String {
		return """
			SELECT
				sources.id,
				source_type AS type,
				source_name AS name,
				source_creator AS creator,
				source_global AS global,
				source_created_on AS created_on,
				account_name AS creator_name,
				(
					SELECT COUNT(*) FROM media
					WHERE media_source = sources.id
				) AS media_count
				${if(extra.orEmpty().isBlank()) "" else ", $extra"}
			FROM sources
			LEFT JOIN accounts ON accounts.id = source_creator
		""".trimIndent()
	}
	/**
	 * SELECT statement for getting info
	 * @return The SELECT statement
	 * @since 1.5.0
	 */
	private fun infoSelect() = infoSelect(null)

	/**
	 * Creates a new media source
	 * @param type The source type (as defined in code, either by the core system or plugins, see [FileSourceManager])
	 * @param name The source's name
	 * @param config The source's configuration (format is dependent on the schema define by the source type)
	 * @param creator The creator's account ID
	 * @param isGlobal Whether the source will be available to all accounts, not just the one that created it
	 * @return The newly created media source's ID
	 * @since 1.5.0
	 */
	suspend fun createSource(type: String, name: String, config: JsonObject, creator: Int, isGlobal: Boolean): Int {
		return SqlTemplate
				.forQuery(client, """
					INSERT INTO sources
					( source_type, source_name, source_config, source_creator, source_global )
					VALUES
					( #{type}, #{name}, CAST( #{config} AS jsonb ), #{creator}, #{global} )
					RETURNING id
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"type" to type,
						"name" to name,
						"config" to config,
						"creator" to creator,
						"global" to isGlobal
				)).await()
				.first().getInteger("id")
	}

	/**
	 * Fetches a source with the specified ID
	 * @param id The source ID to search for
	 * @return The specified source
	 * @since 1.5.0
	 */
	suspend fun fetchSource(id: Int): RowSet<Source> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM sources
					WHERE ${viewWhereFilter()}
					AND sources.id = #{id}
				""".trimIndent())
				.mapTo(Source.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches a source's info with the specified ID
	 * @param id The source ID to search for
	 * @return The specified source's info
	 * @since 1.5.0
	 */
	suspend fun fetchSourceInfo(id: Int): RowSet<SourceInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect("source_config AS config")}
					WHERE ${viewWhereFilter()}
					AND sources.id = #{id}
				""".trimIndent())
				.mapTo(SourceInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}

	/**
	 * Fetches all sources
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the sources
	 * @return All sources
	 * @since 1.5.0
	 */
	suspend fun fetchAllSources(offset: Int, limit: Int, order: Int): RowSet<Source> {
		return SqlTemplate
				.forQuery(client, """
					SELECT * FROM sources
					WHERE ${listWhereFilter()}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(Source.MAPPER)
				.execute(hashMapOf<String, Any>(
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches info for all sources
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the sources
	 * @return Info for all sources
	 * @since 1.5.0
	 */
	suspend fun fetchSources(offset: Int, limit: Int, order: Int): RowSet<SourceInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE ${listWhereFilter()}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(SourceInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches info for all sources with the specified creator
	 * @param creator The creator's ID
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the sources
	 * @return Info for all sources with the specified creator
	 * @since 1.5.0
	 */
	suspend fun fetchSourcesByCreator(creator: Int, offset: Int, limit: Int, order: Int): RowSet<SourceInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE ${listWhereFilter()}
					AND source_creator = #{creator}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(SourceInfo.MAPPER)
				.execute(hashMapOf<String, Any>(
						"creator" to creator,
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches info for all sources with the specified creator and/or matching the provided plaintext query
	 * @param creator The creator's ID (or null if not searching by creator)
	 * @param query The plaintext query (or null if not searching by query)
	 * @param offset The offset of rows to return
	 * @param limit The amount of rows to return
	 * @param order The order to return the sources
	 * @return Info for all sources with the specified creator
	 * @since 1.5.0
	 */
	suspend fun fetchSourcesByCreatorAndOrPlaintextQuery(creator: Int?, query: String?, offset: Int, limit: Int, order: Int): RowSet<SourceInfo> {
		return SqlTemplate
				.forQuery(client, """
					${infoSelect()}
					WHERE ${listWhereFilter()}
					${if(creator == null) "" else "AND source_creator = #{creator}"}
					${if(query == null) "" else "AND (to_tsvector(source_name) @@ plainto_tsquery(#{queryRaw}) OR LOWER(source_name) LIKE LOWER(#{query}))"}
					${orderBy(order)}
					OFFSET #{offset} LIMIT #{limit}
				""".trimIndent())
				.mapTo(SourceInfo.MAPPER)
				.execute(hashMapOf<String, Any?>(
						"creator" to creator,
						"queryRaw" to query,
						"query" to "%$query%",
						"offset" to offset,
						"limit" to limit
				)).await()
	}

	/**
	 * Fetches the total amount of sources (that the account can view, if there is an attached account)
	 * @return The total amount of sources
	 * @since 1.5.0
	 */
	suspend fun fetchSourcesCount(): Int {
		return SqlTemplate
				.forQuery(client, """
					SELECT COUNT(*) FROM sources WHERE ${listWhereFilter()}
				""".trimIndent())
				.execute(mapOf()).await()
				.first().getInteger("count")
	}

	/**
	 * Updates a source's info
	 * @param id The source's ID
	 * @param name The source's new name
	 * @param global Whether the source will now be available to all accounts
	 * @param config The source's new config
	 * @param creator The source's new creator's ID
	 * @since 1.5.0
	 */
	suspend fun updateSourceInfo(id: Int, name: String, global: Boolean, config: JsonObject, creator: Int) {
		SqlTemplate
				.forUpdate(client, """
					UPDATE sources
					SET
						source_name = #{name},
						source_global = #{global},
						source_config = #{config},
						source_creator = #{creator}
					WHERE
					${editWhereFilter()}
					AND id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any?>(
						"id" to id,
						"name" to name,
						"global" to global,
						"config" to config,
						"creator" to creator
				)).await()
	}

	/**
	 * Deletes the source with the specified ID
	 * @param id The source ID
	 * @since 1.5.0
	 */
	suspend fun deleteSource(id: Int) {
		SqlTemplate
				.forUpdate(client, """
					DELETE FROM sources WHERE ${deleteWhereFilter()} AND id = #{id}
				""".trimIndent())
				.execute(hashMapOf<String, Any>(
						"id" to id
				)).await()
	}
}