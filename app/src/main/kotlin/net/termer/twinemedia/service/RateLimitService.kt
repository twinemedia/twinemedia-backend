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
    private val redis: RedisService
) {
    // TODO Terminology: resource and accessor
}