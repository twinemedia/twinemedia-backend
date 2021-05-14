package net.termer.twinemedia.source

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.source.config.ValidationFailedException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

/**
 * Class that manages available media sources
 * @author termer
 * @since 1.5.0
 */
class MediaSourceManager {
	/**
	 * Data class that holds info about a media source
	 * @param id The media source's ID (e.g. "my_source")
	 * @param name The media source's name
	 * @param description The media source's description
	 * @param sourceClass The media source's class
	 * @since 1.5.0
	 */
	class Info(val id: String, val name: String, val description: String, val sourceClass: Class<out MediaSource>)

	/**
	 * Data class that holds an instance and its expiration time
	 * @param source The source instance (can be null if not yet created)
	 * @param sourceClass The source class to use for instantiating
	 * @param config The configuration to use when creating
	 * @param expireTime The time it expires (or null for none)
	 * @since 1.5.0
	 */
	class Instance(var source: MediaSource?, val sourceClass: Class<out MediaSource>, val config: JsonObject, var expireTime: OffsetDateTime?)

	private val sources = ArrayList<Info>()
	private val instances = ConcurrentHashMap<Int, Instance>()

	/**
	 * Initializes this manager
	 * @since 1.5.0
	 */
	fun initialize() {
		// TODO Every minute or so, delete (and shutdown) instances that are past their expiration

		vertx().setPeriodic(60*1000) {
			val now = Date().toInstant().atOffset(ZoneOffset.UTC)
			val keys = instances.keys()

			for(key in keys) {
				val instance = instances[key]!!

				// Check if instance is expired
				if(instance.source != null && instance.expireTime != null && now.isAfter(instance.expireTime)) {
					// Remove source instance
					val source = instance.source
					instance.source = null

					// Shutdown instance if stateful
					if(source is StatefulMediaSource) {
						GlobalScope.launch(vertx().dispatcher()) {
							try {
								source.shutdown()
							} catch(e: Exception) {
								logger.error("Failed to shutdown instance of media source instance ID $key (${instance.sourceClass.name}):")
								e.printStackTrace()
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Returns all available media sources
	 * @return All available media sources
	 * @since 1.5.0
	 */
	fun availableSources() = sources.toTypedArray()

	/**
	 * Returns the media source instance with the provided ID, or null if none exists
	 * @param id The media source instance ID
	 * @param minutesToKeep The amount of minutes to keep this instance alive before destroying it (defaults to 60)
	 * @return The media source with the provided ID
	 * @since 1.5.0
	 */
	suspend fun getSourceInstanceById(id: Int, minutesToKeep: Int? = 60): MediaSource? {
		if(instances.containsKey(id)) {
			val instance = instances[id]!!

			// Check if instance is initialized
			return if(instance.source == null) {
				// Instantiate
				val source = instance.sourceClass.newInstance()

				// Configure
				source.getConfig().configure(instance.config)

				// Startup if stateful
				if(source is StatefulMediaSource)
					source.startup()

				// Set expire time if time is provided, otherwise set to never expire
				if(minutesToKeep == null)
					instance.expireTime = null
				else
					instance.expireTime = Date().toInstant().atOffset(ZoneOffset.UTC).plusMinutes(minutesToKeep.toLong())

				// Set source
				instance.source = source

				// Return
				source
			} else {
				// Return existing instance
				instance.source
			}
		} else {
			return null
		}
	}

	/**
	 * Registers a media source under the provided name and description
	 * @param id The media source's ID (e.g. "my_source")
	 * @param name The media source's name
	 * @param description The media source's description
	 * @param clazz The media source's class
	 * @return This, to be used fluently
	 * @since 1.5.0
	 */
	fun registerSource(id: String, name: String, description: String, clazz: Class<out MediaSource>): MediaSourceManager {
		sources.add(Info(id, name, description, clazz))
		return this
	}

	/**
	 * Registers an instance of a media source
	 * @param instanceId The instance ID (to be referenced later)
	 * @param sourceClass The media source class to use for creating the instance
	 * @param config The configuration to use for the instance
	 * @since 1.5.0
	 */
	fun registerInstance(instanceId: Int, sourceClass: Class<out MediaSource>, config: JsonObject) {
		// Create instance to test config
		val instance = sourceClass.newInstance()

		// Validate config
		val valRes = instance.getConfig().getSchema().validate(config)

		if(valRes.valid)
			instances[instanceId] = Instance(null, sourceClass, config, null)
		else
			throw ValidationFailedException("Validation for the provided JSON does not match the schema: "+valRes.errorText)
	}

	/**
	 * Deletes (and shuts down) an instance with the specified ID, if it exists
	 * @param instanceId The instance ID
	 * @since 1.5.0
	 */
	suspend fun deleteInstance(instanceId: Int) {
		// Check if instance exists
		if(instances.containsKey(instanceId)) {
			val source = instances[instanceId]!!.source

			// Remove instance
			instances.remove(instanceId)

			// Shutdown if source is initialized and stateful
			if(source is StatefulMediaSource)
				source.shutdown()
		}
	}
}