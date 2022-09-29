package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.sqlclient.templates.RowMapper
import net.termer.twinemedia.util.JsonSerializable

/**
 * Data class for a tag
 * @since 1.4.0
 */
class Tag(
	/**
	 * The tag's internal sequential ID
	 * @since 2.0.0
	 */
	val id: Int,

	/**
	 * The tag's name
	 * @since 1.4.0
	 */
	val name: String,

	/**
	 * How many files are using the tag
	 * @since 1.4.0
	 */
	val files: Int
): JsonSerializable {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"files" to files
	)

	companion object {
		/**
		 * The row mapper for this type of row
		 * @since 1.4.0
		 */
		val MAPPER = RowMapper { row ->
			Tag(
				id = row.getInteger("id"),
				name = row.getString("name"),
				files = row.getInteger("files")
			)
		}
	}
}