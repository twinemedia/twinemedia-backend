package net.termer.twinemedia.service

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.Response
import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.Constants.DEFAULT_REDIS_KEY_PREFIX
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.urlEncode
import kotlin.text.Charsets.UTF_8

/**
 * Service for interfacing with Redis
 * @since 2.0.0
 */
class RedisService(
    /**
     * The Vert.x instance to use
     */
    vertx: Vertx,

    /**
     * The host the Redis server is running on
     */
    host: String,

    /**
     * The port the Redis server is running on
     */
    port: Int,

    /**
     * The username to use when authenticating with the Redis server, or null for none (defaults to null)
     */
    username: String? = null,

    /**
     * The password to use when authenticating with the Redis server, or null for none (defaults to null)
     */
    password: String? = null,

    /**
     * The prefix to use on all Redis keys (defaults to [DEFAULT_REDIS_KEY_PREFIX])
     */
    private val keyPrefix: String = DEFAULT_REDIS_KEY_PREFIX
) {
    companion object {
        private var _instance: RedisService? = null

        /**
         * The global [RedisService] instance.
         * Will throw [NullPointerException] if accessed before [initInstance] is called.
         * @since 2.0.0
         */
        val INSTANCE
            get() = _instance!!

        /**
         * Initializes the global [INSTANCE] singleton
         * @param config The [AppConfig] to use for the singleton creation
         * @since 2.0.0
         */
        suspend fun initInstance(vertx: Vertx, config: AppConfig) {
            _instance = RedisService(vertx, config.redisHost, config.redisPort, config.redisAuthUser, config.redisAuthPassword)
            _instance!!.connect()
        }
    }

    /**
     * The underlying [Redis] instance used for interacting with the Redis server
     * @since 2.0.0
     */
    val redis: Redis

    /**
     * The underlying [RedisAPI] instance used for interacting with the Redis client
     * @since 2.0.0
     */
    val api: RedisAPI

    init {
        val connUrl =
            if (password == null)
                "redis://$host:$port"
            else
                "redis://${username?.urlEncode() ?: ""}:${password.urlEncode()}@$host:$port"

        redis = Redis.createClient(vertx, connUrl)
        api = RedisAPI.api(redis)
    }

    private inline fun mkKey(key: String) = keyPrefix + key

    /**
     * Connects to the Redis server
     * @since 2.0.0
     */
    suspend fun connect() {
        redis.connect().await()
    }

    /**
     * Gets a value by its key
     * @param key the key
     * @return The value, or null if no value with that key exists
     * @since 2.0.0
     */
    suspend fun get(key: String): Response? {
        return api.get(mkKey(key)).await()
    }

    /**
     * Gets a string value by its key
     * @param key The key
     * @return The value, or null if no value with that key exists
     * @since 2.0.0
     */
    suspend fun getString(key: String): String? {
        return get(key)?.toString(UTF_8)
    }

    /**
     * Gets an integer value by its key
     * @param key The key
     * @return The value, or null if no value with that key exists
     * @since 2.0.0
     */
    suspend fun getInt(key: String): Int? {
        return get(key)?.toInteger()
    }

    /**
     * Gets a long integer value by its key
     * @param key The key
     * @return The value, or null if no value with that key exists
     * @since 2.0.0
     */
    suspend fun getLong(key: String): Long? {
        return get(key)?.toLong()
    }

    /**
     * Gets a [JsonObject] value by its key
     * @param key The key
     * @return The value, or null if no value with that key exists
     * @since 2.0.0
     */
    suspend fun getJsonObject(key: String): JsonObject? {
        val res = getString(key)

        return if (res == null) null else JsonObject(res)
    }

    /**
     * Sets a string for a key, optionally expiring in the specified number of seconds.
     * Because all normal values in Redis are stored as strings, this method can be used for any type.
     * @param key The key
     * @param value The value
     * @param expireSeconds The number of seconds for the value to live before expiring, or <1 for no expiration time (defaults to 0)
     * @since 2.0.0
     */
    suspend fun setString(key: String, value: String, expireSeconds: Int = 0) {
        if (expireSeconds > 0)
            api.setex(mkKey(key), expireSeconds.toString(), value).await()
        else
            api.set(listOf(mkKey(key), value)).await()
    }

    /**
     * Sets a number for a key, optionally expiring in the specified number of seconds
     * @param key The key
     * @param value The value
     * @param expireSeconds The number of seconds for the value to live before expiring, or <1 for no expiration time (defaults to 0)
     * @since 2.0.0
     */
    suspend fun setNumber(key: String, value: Number, expireSeconds: Int = 0) {
        setString(key, value.toString(), expireSeconds)
    }

    /**
     * Sets a [JsonObject] for a key, optionally expiring in the specified number of seconds
     * @param key The key
     * @param value The value
     * @param expireSeconds The number of seconds for the value to live before expiring, or <1 for no expiration time (defaults to 0)
     * @since 2.0.0
     */
    suspend fun setJsonObject(key: String, value: JsonObject, expireSeconds: Int = 0) {
        setString(key, value.encode(), expireSeconds)
    }

    /**
     * Sets a [JsonObject] for a key using a [JsonSerializable], optionally expiring in the specified number of seconds
     * @param key The key
     * @param value The value
     * @param expireSeconds The number of seconds for the value to live before expiring, or <1 for no expiration time (defaults to 0)
     * @since 2.0.0
     */
    suspend fun setJsonObject(key: String, value: JsonSerializable, expireSeconds: Int = 0) {
        setJsonObject(key, value.toJson(), expireSeconds)
    }

    /**
     * Increments an integer value by its key and returns the new value
     * @param key The key
     * @return The new value
     * @since 2.0.0
     */
    suspend fun incrementInt(key: String): Int {
        return api.incr(mkKey(key)).await().toInteger()
    }

    /**
     * Decrements an integer value by its key and returns the new value
     * @param key The key
     * @return The new value
     * @since 2.0.0
     */
    suspend fun incrementLong(key: String): Long {
        return api.incr(mkKey(key)).await().toLong()
    }

    /**
     * Decrements a long integer value by its key and returns the new value
     * @param key The key
     * @return The new value
     * @since 2.0.0
     */
    suspend fun decrementInt(key: String): Int {
        return api.decr(mkKey(key)).await().toInteger()
    }

    /**
     * Decrements a long integer value by its key and returns the new value
     * @param key The key
     * @return The new value
     * @since 2.0.0
     */
    suspend fun decrementLong(key: String): Long {
        return api.incr(mkKey(key)).await().toLong()
    }

    /**
     * Deletes a key
     * @param key The key
     * @return Whether a key was deleted
     * @since 2.0.0
     */
    suspend fun delete(key: String): Boolean {
        return api.del(listOf(mkKey(mkKey(key)))).await().toInteger() > 0
    }

    /**
     * Deletes many keys
     * @param keys The keys to delete
     * @return The number of keys that were deleted
     * @since 2.0.0
     */
    suspend fun deleteMany(keys: List<String>): Int {
        return api.del(keys.map { mkKey(it) }).await().toInteger()
    }

    /**
     * Returns whether the specified key exists
     * @param key The key
     * @return Whether the key exists
     * @since 2.0.0
     */
    suspend fun exists(key: String): Boolean {
        return api.exists(listOf(mkKey(key))).await().toInteger() > 0
    }

    /**
     * Returns the number of the specified keys that exist
     * @param keys The keys
     * @return The number of the specified keys that exist
     * @since 2.0.0
     */
    suspend fun existsMany(keys: List<String>): Int {
        return api.exists(keys.map { mkKey(it) }).await().toInteger()
    }
}
