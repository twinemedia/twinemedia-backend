package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.toStringArray
import java.time.OffsetDateTime

/**
 * Data class for a process
 * @param id The process's ID
 * @param mime The MIME type this process applies to
 * @param settings The process's settings
 * @param creator The process creator's ID
 * @param createdOn The time this process was created
 * @param modifiedOn The time this process was last modified
 * @since 1.4.0
 */
@DataObject
class Process(
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
		 * The process creator's ID
		 * @since 1.4.0
		 */
		val creator: Int,
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
) {
	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<Process> { row ->
			Process(
					id = row.getInteger("id"),
					mime = row.getString("process_mime"),
					settings = row.getJsonObject("process_settings"),
					creator = row.getInteger("process_creator"),
					createdOn = row.getOffsetDateTime("process_created_on"),
					modifiedOn = row.getOffsetDateTime("process_modified_on")
			)
		}
	}
}