package net.termer.twinemedia

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import net.termer.twinemedia.Constants.SPECIAL_CHARS
import net.termer.twinemedia.util.JsonSerializable

/**
 * TwineMedia configuration class
 * @since 1.0.0
 */
class AppConfig(
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
     * Whether to respect X-Forwarded-For headers to determine a client's IP address
     * @since 2.0.0
     */
    var respectXff: Boolean = true,

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
    var maxUploadSize: Long = 1073741824, // 1GB

    /**
     * The application database's host
     * @since 2.0.0
     */
    var dbHost: String = "localhost",

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
     * @sinec 2.0.0
     */
    var dbAutoMigrate: Boolean = true,

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
    var jwtExpireMinutes: Int = 120,

    /**
     * The number of threads to use for password hashing
     * @since 2.0.0
     */
    var passwordHashThreadCount: Int = 1,

    /**
     * The amount of memory to use for password hashing, in kilobytes
     * @since 2.0.0
     */
    var passwordHashMemoryKb: Int = 2048,

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
    var httpServerThreads: Int = 0
): JsonSerializable {
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