package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import net.termer.twinemedia.util.JsonSerializable

/**
 * DTO for a record's file source.
 * Used in properties of other DTOs.
 * @since 2.0.0
 */
class RecordSourceDto(
	/**
	 * The file source's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

	/**
	 * The file source's name
	 * @since 2.0.0
	 */
	val name: String,

	/**
	 * The file source's type
	 * @since 2.0.0
	 */
	val type: String
): JsonSerializable {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name,
		"type" to type
	)
}