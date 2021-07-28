package net.termer.twinemedia.db.dataobject

import io.vertx.core.json.JsonObject

/**
 * Interface that defines the methods for serializable data objects
 * @author termer
 * @since 1.5.0
 */
interface SerializableDataObject {
	/**
	 * Returns a JSON representation of the data object's info
	 * @return A JSON representation of the data object's info
	 * @since 1.5.0
	 */
	fun toJson(): JsonObject
}