package net.termer.twinemedia.db.dataobject

import io.vertx.codegen.annotations.DataObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.templates.RowMapper

/**
 * Data class for a tag
 * @param name The tag's name
 * @param files How many files have the tag
 * @since 1.4.0
 */
@DataObject
class Tag(
		/**
		 * The tag's name
		 * @since 1.4.0
		 */
		val name: String,
		/**
		 * How many files have the tag
		 * @since 1.4.0
		 */
		val files: Int
): SerializableDataObject {
	override fun toJson() = json {obj(
			"name" to name,
			"files" to files
	)}

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper<Tag> { row ->
			Tag(
					name = row.getString("name"),
					files = row.getInteger("files")
			)
		}
	}
}