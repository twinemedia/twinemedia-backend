package net.termer.twinemedia

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.DEFAULT_REDIS_KEY_PREFIX
import net.termer.twinemedia.util.JsonSerializable
import net.termer.twinemedia.util.SPECIAL_CHARS

/**
 * TwineMedia configuration class
 * @since 1.0.0
 */
data class AppConfig(
    /**
     * The application HTTP server's bind host
     * @since 2.0.0
     */
    var bindHost: String = "127.0.0.1",

    /**
     * The application HTTP server's bind port
     * @since 2.0.0
     */
    var bindPort: Int = 8080,

    /**
     * The reverse proxy header to use for getting the origin IP address of a request, or null to not use any
     * @since 2.0.0
     */
    var reverseProxyIpHeader: String? = null,

    /**
     * Path where uploads are temporarily stored before being sent to their destination storage sources.
     * At runtime, the path will be absolute and NOT end with a trailing slash.
     * @since 2.0.0
     */
    var uploadsTmpPath: String = "tm-data/file-uploads",

    /**
     * Path where processed media files are temporarily stored while processing and before being sent to their destination storage sources
     * At runtime, the path will be absolute and NOT end with a trailing slash.
     * @since 2.0.0
     */
    var mediaProcessingTmpPath: String = "tm-data/processing-media",

    /**
     * Path where file thumbnails are stored.
     * At runtime, the path will be absolute and NOT end with a trailing slash.
     * @since 2.0.0
     */
    var thumbnailsStoragePath: String = "tm-data/thumbnails/",

    /**
     * The maximum file upload size, in bytes
     * @since 2.0.0
     */
    var maxUploadSize: Long = 5368709120, // 5GiB

    /**
     * The maximum number of concurrent uploads per account
     * @since 2.0.0
     */
    var maxConcurrentUploads: Int = 5,

    /**
     * The application database's host
     * @since 2.0.0
     */
    var dbHost: String = "127.0.0.1",

    /**
     * The application database's port
     * @since 2.0.0
     */
    var dbPort: Int = 5432,

    /**
     * The application database's name
     * @since 2.0.0
     */
    var dbName: String = "twinemedia",

    /**
     * The user to use for authenticating with the application database
     * @since 2.0.0
     */
    var dbAuthUser: String = "me",

    /**
     * The password to use for authenticating with the application database
     * @since 2.0.0
     */
    var dbAuthPassword: String = "drowssap",

    /**
     * The maximum number of open connections to keep in the database connection pool
     * @since 2.0.0
     */
    var dbMaxPoolSize: Int = 5,

    /**
     * Whether to apply new database migrations on application startup
     * @since 2.0.0
     */
    var dbAutoMigrate: Boolean = true,

    /**
     * The Redis server's host
     * @since 2.0.0
     */
    var redisHost: String = "127.0.0.1",

    /**
     * The Redis server's port
     * @since 2.0.0
     */
    var redisPort: Int = 6379,

    /**
     * The user to use for authenticating with the Redis server, or null for none
     * @since 2.0.0
     */
    var redisAuthUser: String? = null,

    /**
     * The password to use for authenticating with the Redis server, or null for none
     * @since 2.0.0
     */
    var redisAuthPassword: String? = null,

    /**
     * The key prefix to use for Redis calls.
     * This should only be changed if the prefix happens to conflict with other keys on the Redis server.
     * In most cases, you should keep this the way it is.
     * @since 2.0.0
     */
    var redisKeyPrefix: String = DEFAULT_REDIS_KEY_PREFIX,

    /**
     * The secret to use for signing JWT keys.
     * This value must be long and secure, otherwise bad actors could create and sign their own keys.
     * @since 2.0.0
     */
    var jwtSecret: String = "jwtauth_please_change_me",

    /**
     * The amount of minutes JWT tokens should last before becoming invalid
     * @since 2.0.0
     */
    var jwtExpireMinutes: Int = 1440, // 24 hours

    /**
     * The number of threads to use for password hashing
     * @since 2.0.0
     */
    var passwordHashThreadCount: Int = 1,

    /**
     * The amount of memory to use for password hashing, in kibibytes
     * @since 2.0.0
     */
    var passwordHashMemoryKib: Int = 2048,

    /**
     * The HTTP origin to allow requests from, or "*" to accept from any origin
     * @since 2.0.0
     */
    var apiAllowOrigin: String = "*",

    /**
     * The path to the FFmpeg executable to use
     * @since 2.0.0
     */
    var ffmpegPath: String = "/usr/bin/ffmpeg",

    /**
     * The path to the FFprobe executable to use
     * @since 2.0.0
     */
    var ffprobePath: String = "/usr/bin/ffprobe",

    /**
     * The number of media processor queues to concurrently run
     * @since 2.0.0
     */
    var mediaProcessorQueues: Int = 2,

    /**
     * The maximum number of failed authentication attempts allowed before imposing a timeout on the IP address
     * @since 2.0.0
     */
    var authMaxFailedAttempts: Int = 5,

    /**
     * The timeout in seconds to impose after an IP address makes too many failed authentication attempts
     * @since 2.0.0
     */
    var authTimeoutSeconds: Int = 300,

    /**
     * The minimum required password length
     * @since 2.0.0
     */
    var passwordRequireLength: Int = 8,

    /**
     * Whether passwords will be required to contain an uppercase letter
     * @since 2.0.0
     */
    var passwordRequireUppercase: Boolean = true,

    /**
     * Whether passwords will be required to contain a number
     * @since 2.0.0
     */
    var passwordRequireNumber: Boolean = true,

    /**
     * Whether passwords will be required to contain a special character.
     * See [SPECIAL_CHARS] for the list of special characters used to evaluate this criterion.
     * @since 2.0.0
     */
    var passwordRequireSpecial: Boolean = true,

    /**
     * The number of CPU threads to use for HTTP.
     * Any value under 1 indicates that the thread count will be all available threads minus the value.
     * For example, on a machine that has 12 threads, a value of -2 will result in 10 threads being used.
     * @since 2.0.0
     */
    var httpServerThreads: Int = 0,

    /**
     * An ideally 26 character long encryption key for the application to use.
     * This value must be secure, otherwise bad actors could decrypt sensitive data and create their own trusted payloads.
     * If this value or [encryptionSalt] changes, various values such as pagination tokens will be invalidated.
     * Care must be exercised when changing these values.
     * @since 2.0.0
     */
    var encryptionKey: String = "something_very_random_and_very_secure",

    /**
     * An ideally 8 character long encryption salt for application use.
     * If this value or [encryptionKey] changes, various values such as pagination tokens will be invalidated.
     * Care must be exercised when changing these values.
     * @since 2.0.0
     */
    var encryptionSalt: String = "something_very_random"
): JsonSerializable() {
    companion object {
        /**
         * Deserializes the JSON representation of an [AppConfig] to a new [AppConfig] object
         * @param json The JSON to deserialize
         * @return The resulting [AppConfig] object
         * @throws DecodeException If decoding the JSON fails
         * @throws IllegalArgumentException If the JSON is valid but a field is missing, invalid, etc
         * @since 2.0.0
         */
        @Throws(DecodeException::class, IllegalArgumentException::class)
        fun fromJson(json: JsonObject): AppConfig = json.mapTo(AppConfig::class.java)
    }

    override fun toJson(): JsonObject = JsonObject.mapFrom(this)
    override fun toString(): String = toJson().encode()
}