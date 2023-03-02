package net.termer.twinemedia.util

import io.vertx.core.json.JsonObject

/**
 * Interface for classes that can be serialized to a [JsonObject]
 * @since 2.0.0
 */
abstract class JsonSerializable : Iterable<Map.Entry<String, Any>> {
	/**
	 * Returns a JSON-serialized representation of this object
	 * @since 2.0.0
	 */
	abstract fun toJson(): JsonObject

	/**
	 * Returns the JSON representation of this object
	 * @since 2.0.0
	 */
	override fun toString() = toJson().toString()

	override fun iterator() = toJson().iterator()
}