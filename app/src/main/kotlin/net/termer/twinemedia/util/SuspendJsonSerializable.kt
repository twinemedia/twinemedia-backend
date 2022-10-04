package net.termer.twinemedia.util

import io.vertx.core.json.JsonObject

/**
 * Interface for similar functionality as [JsonSerializable], but [toJson] is a suspend method
 * @since 2.0.0
 */
interface SuspendJsonSerializable {
	/**
	 * Returns a JSON-serialized representation of this object
	 * @since 2.0.0
	 */
	suspend fun toJson(): JsonObject
}