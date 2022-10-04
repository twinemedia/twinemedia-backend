package net.termer.twinemedia

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.util.JsonSerializable

/**
 * Data object containing various app resources such as config info
 * @since 2.0.0
 * @author termer
 */
data class AppContext(
	/**
	 * The app config
	 * @since 2.0.0
	 */
	val config: AppConfig = AppConfig()
): JsonSerializable() {
	companion object {
		/**
		 * Deserializes the JSON representation of an [AppContext] to a new [AppContext] object
		 * @param json The JSON to deserialize
		 * @return The resulting [AppContext] object
		 * @throws DecodeException If decoding the JSON fails
		 * @throws IllegalArgumentException If the JSON is valid but a field is missing, invalid, etc
		 * @since 2.0.0
		 */
		@Throws(DecodeException::class, IllegalArgumentException::class)
		fun fromJson(json: JsonObject): AppContext = json.mapTo(AppContext::class.java)
	}

	override fun toJson(): JsonObject = JsonObject.mapFrom(this)
	override fun toString(): String = toJson().encode()
}