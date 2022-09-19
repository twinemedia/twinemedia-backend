package net.termer.twinemedia.source

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twinemedia.source.config.ValidationFailedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

/**
 * Class that manages available file sources
 * @param vertx The Vert.x instance to use
 * @author termer
 * @since 1.5.0
 */
@DelicateCoroutinesApi
class FileSourceManager(private val vertx: Vertx) {
	/**
	 * Data class that holds info about a file source
	 * @since 1.5.0
	 */
	data class Info(
		/**
		 * The source's type identifier (e.g. "my_source")
		 * @since 1.5.0
		 */
		val type: String,

		/**
		 * The source's name
		 * @since 1.5.0
		 */
		val name: String,

		/**
		 * The source's description
		 * @since 1.5.0
		 */
		val description: String,

		/**
		 * The source's class
		 * @since 1.5.0
		 */
		val sourceClass: Class<out FileSource>
	)

	/**
	 * Data class that holds a source instance and its expiration time
	 * @since 1.5.0
	 */
	data class Instance(
		/**
		 * The source instance (can be null if not yet created)
		 * @since 1.5.0
		 */
		var source: FileSource?,

		/**
		 * The source class to use for instantiating
		 * @since 1.5.0
		 */
		val sourceClass: Class<out FileSource>,

		/**
		 * The configuration to use when creating
		 * @since 1.5.0
		 */
		val config: JsonObject,

		/**
		 * The time it expires (or null for none)
		 * @since 1.5.0
		 */
		var expireTime: OffsetDateTime?
	)

	private val logger: Logger = LoggerFactory.getLogger(FileSourceManager::class.java)
	private val sources = ArrayList<Info>()
	private val instances = ConcurrentHashMap<Int, Instance>()

	/**
	 * Initializes this manager
	 * @since 1.5.0
	 */
	fun initialize() {
		// Every minute, delete (and shutdown if StatefulFileSource) instances that are past their expiration
		vertx.setPeriodic(60_000L) {
			val now = Date().toInstant().atOffset(ZoneOffset.UTC)
			val keys = instances.keys()

			for(key in keys) {
				val instance = instances[key]!!

				// Check if instance is expired and not locked
				if(instance.source != null && instance.expireTime != null && now.isAfter(instance.expireTime) && !instance.source!!.lock.locked()) {
					// Remove source instance
					val source = instance.source
					instance.source = null

					// Shutdown instance if stateful
					if(source is StatefulFileSource) {
						GlobalScope.launch(vertx.dispatcher()) {
							try {
								source.shutdown()
							} catch(e: Exception) {
								logger.error("Failed to shutdown instance of file source instance ID $key (${instance.sourceClass.name}):")
								e.printStackTrace()
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Returns all available file sources
	 * @return All available file sources
	 * @since 1.5.0
	 */
	fun availableSources() = sources.toTypedArray()

	/**
	 * Returns the available source with the specified type name, or null if none exists
	 * @param type The type name to search for
	 * @return The available source with the specified type name, or null if none exists
	 * @since 1.5.0
	 */
	@Suppress("DEPRECATION")
	fun getAvailableSourceByTypeName(type: String): Info? {
		@Suppress("DEPRECATION")
		for(src in sources)
			if(src.type == type)
				return src

		return null
	}

	/**
	 * Returns all registered source instances
	 * @return All registered source instances
	 * @since 1.5.0
	 */
	fun registeredSourceInstances() = instances.values.toTypedArray()

	/**
	 * Returns the file source instance with the provided ID, or null if none exists
	 * @param id The file source instance ID
	 * @param minutesToKeep The amount of minutes to keep this instance alive before destroying it (defaults to 60)
	 * @return The file source with the provided ID
	 * @since 1.5.0
	 */
	suspend fun getSourceInstanceById(id: Int, minutesToKeep: Int? = 60): FileSource? {
		if(instances.containsKey(id)) {
			val instance = instances[id]!!

			// Check if instance is initialized
			return if(instance.source == null) {
				// Instantiate
				val source = instance.sourceClass
					.getDeclaredConstructor()
					.newInstance()

				// Configure
				source.config.configure(instance.config)

				// Startup if stateful
				if(source is StatefulFileSource)
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
	 * Registers a file source under the provided name and description
	 * @param id The file source's ID (e.g. "my_source")
	 * @param name The file source's name
	 * @param description The file source's description
	 * @param clazz The file source's class
	 * @return This, to be used fluently
	 * @since 1.5.0
	 */
	fun registerSource(id: String, name: String, description: String, clazz: Class<out FileSource>): FileSourceManager {
		sources.add(Info(id, name, description, clazz))
		return this
	}

	/**
	 * Registers an instance of a file source
	 * @param instanceId The instance ID (to be referenced later)
	 * @param sourceClass The file source class to use for creating the instance
	 * @param config The configuration to use for the instance
	 * @since 1.5.0
	 */
	fun registerInstance(instanceId: Int, sourceClass: Class<out FileSource>, config: JsonObject) {
		// Create instance to test config
		@Suppress("DEPRECATION")
		val instance = sourceClass.newInstance()

		// Validate config
		val valRes = instance.config.schema.validate(config)

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
			if(source is StatefulFileSource)
				source.shutdown()
		}
	}
}