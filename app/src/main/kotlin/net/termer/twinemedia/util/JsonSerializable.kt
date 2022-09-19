package net.termer.twinemedia.util

import io.vertx.core.json.JsonObject

/**
 * Interface for classes that can be serialized to a [JsonObject]
 * @since 2.0.0
 */
interface JsonSerializable {
	/**
	 * Returns a JSON-serialized representation of this object
	 * @since 1.0.0
	 */
	fun toJson(): JsonObject
}