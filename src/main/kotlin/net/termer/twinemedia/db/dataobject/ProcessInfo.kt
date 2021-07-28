package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for a process's info
 * @param id The process's ID
 * @param mime The MIME type this process applies to
 * @param settings The process's settings
 * @param creator The process creator's ID
 * @param creatorName The process creator's name (null if the account doesn't exist)
 * @param createdOn The time this process was created
 * @param modifiedOn The time this process was last modified
 * @since 1.4.0
 */
@DataObject
class ProcessInfo(
		/**
		 * The process's ID
		 * @since 1.4.0
		 */
		val id: Int,
		/**
		 * The MIME type this process applies to
		 * @since 1.4.0
		 */
		val mime: String,
		/**
		 * The process's settings
		 * @since 1.4.0
		 */
		val settings: JsonObject,
		/**
		 * The process's output file extension
		 * @since 1.4.0
		 */
		val extension: String,
		/**
		 * The process creator's ID
		 * @since 1.4.0
		 */
		val creator: Int,
		/**
		 * The process creator's name (null if the account doesn't exist)
		 * @since 1.4.0
		 */
		val creatorName: String?,
		/**
		 * The time this process was created
		 * @since 1.4.0
		 */
		val createdOn: OffsetDateTime,
		/**
		 * The time this process was last modified
		 * @since 1.4.0
		 */
		val modifiedOn: OffsetDateTime
): SerializableDataObject {
	override fun toJson() = json {obj(
			"id" to id,
			"mime" to mime,
			"settings" to settings,
			"creator" to creator,
			"creator_name" to creatorName,
			"created_on" to createdOn.toString(),
			"modified_on" to modifiedOn.toString()
	)}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<ProcessInfo> { row ->
			ProcessInfo(
					id = row.getInteger("id"),
					mime = row.getString("mime"),
					settings = row.getJsonObject("settings"),
					extension = row.getString("extension"),
					creator = row.getInteger("creator"),
					creatorName = row.getString("creator_name"),
					createdOn = row.getOffsetDateTime("created_on"),
					modifiedOn = row.getOffsetDateTime("modified_on")
			)
		}
	}
}