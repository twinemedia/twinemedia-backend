package net.termer.twinemedia.service

/**
 * Service for handling rate limiting
 * @since 2.0.0
 */
class RateLimitService(
    /**
     * The [RedisService] to use for storing rate limit state
     * @since 2.0.0
     */
    private val redisService: RedisService
) {
    companion object {
        private var _instance: RateLimitService? = null

        /**
         * The global [RateLimitService] instance.
         * Will throw [NullPointerException] if accessed before [initInstance] is called.
         * @since 2.0.0
         */
        val INSTANCE
            get() = _instance!!

        /**
         * Initializes the global [INSTANCE] singleton
         * @param redisService The [RedisService] to use for the singleton creation
         * @since 2.0.0
         */
        suspend fun initInstance(redisService: RedisService) {
            _instance = RateLimitService(redisService)
        }
    }

    private inline fun mkKey(resource: String, accessor: String) = "rl_${resource}_$accessor"

    /**
     * Records a hit on a resource by an accessor and returns whether the maximum number of hits for the specified window was reached
     * @param resource The resource identifier (e.g. "LOGIN_PAGE")
     * @param accessor The accessor identifier (e.g. an IP address)
     * @param windowSeconds The rate limit time window, in seconds
     * @param windowMaxHits The maximum number of hits allowed in within the rate limit time window
     * @return Whether the maximum number of hits for the specified window was reached
     * @since 2.0.0
     */
    suspend fun hitAndCheck(resource: String, accessor: String, windowSeconds: Int, windowMaxHits: Int): Boolean {
        val key = mkKey(resource, accessor)

        val hits: Int
        if (redisService.exists(key)) {
            hits = redisService.incrementInt(key)
        } else {
            hits = 1
            redisService.setNumber(key, hits, windowSeconds)
        }

        return hits > windowMaxHits
    }

    /**
     * Returns whether the maximum number of hits was reached.
     * Usually you should use [hitAndCheck] because it automatically records a hit.
     * Use this method only for operations that require you to not modify rate limit state.
     * @param resource The resource identifier (e.g. "LOGIN_PAGE")
     * @param accessor The accessor identifier (e.g. an IP address)
     * @param windowMaxHits The maximum number of hits allowed
     * @return Whether the maximum number of hits was reached.
     * @since 2.0.0
     */
    suspend fun check(resource: String, accessor: String, windowMaxHits: Int): Boolean {
        val hits = redisService.getInt(mkKey(resource, accessor))

        return hits != null && hits >= windowMaxHits
    }
}
