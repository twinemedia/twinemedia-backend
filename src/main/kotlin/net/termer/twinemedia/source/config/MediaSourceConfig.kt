package net.termer.twinemedia.source.config

import io.vertx.core.json.JsonObject

/**
 * Interface that defines a media source configuration, including configuration schemas and JSON handling
 * @author termer
 * @since 1.5.0
 */
interface MediaSourceConfig {
	/**
	 * Returns the config's schema
	 * @return The config's schema
	 * @since 1.5.0
	 */
	fun getSchema(): MediaSourceSchema

	/**
	 * Takes in a JSON object and sets the configuration based on it
	 * @param json The JSON to use for configuring
	 * @throws ValidationFailedException If the provided JSON does not adhere to this config's schema
	 * @since 1.5.0
	 */
	fun configure(json: JsonObject)
}