package net.termer.twinemedia

/**
 * Application constants
 * @since 1.4.1
 */
object Constants {
	/**
	 * The application name
	 * @since 2.0.0
	 */
	const val APP_NAME = "TwineMedia"

	/**
	 * The application version
	 * @since 1.4.1
	 */
	const val VERSION = "2.0.0"

	/**
	 * The application's current API version
	 * @since 2.0.0
	 */
	const val CURRENT_API_VERSION = "v2"

	/**
	 * The application's supported API versions
	 * @since 1.4.1
	 */
	val API_VERSIONS = arrayOf("v2")

	/**
	 * The maximum API result limit
	 * @since 2.0.0
	 */
	const val API_MAX_RESULT_LIMIT = 100

	/**
	 * The default Redis key prefix used by the application
	 * @since 2.0.0
	 */
	const val DEFAULT_REDIS_KEY_PREFIX = "tm_"

	/**
	 * The default reverse proxy IP header
	 * @since 2.0.0
	 */
	const val DEFAULT_REVERSE_PROXY_IP_HEADER = "X-Forwarded-For"
}