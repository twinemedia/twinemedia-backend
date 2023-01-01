package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import net.termer.twinemedia.util.JsonSerializable

/**
 * DTO for a record's owner account.
 * Used in properties of other DTOs.
 * @since 2.0.0
 */
class RecordOwnerDto(
	/**
	 * The owner account's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String,

	/**
	 * The owner account's name
	 * @since 2.0.0
	 */
	val name: String
): JsonSerializable() {
	override fun toJson() = jsonObjectOf(
		"id" to id,
		"name" to name
	)
}
