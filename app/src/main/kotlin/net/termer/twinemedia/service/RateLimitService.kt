package net.termer.twinemedia.service

import io.vertx.ext.web.RoutingContext
import net.termer.krestx.api.util.ApiErrorResponse
import net.termer.krestx.api.util.apiError
import net.termer.twinemedia.util.ip

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

    /**
     * A rate limit preset.
     * Contains all parameters for rate limiting functions, except for the accessor.
     * @since 2.0.0
     */
    data class Preset(
        /**
         * The resource identifier (e.g. "LOGIN_PAGE")
         * @since 2.0.0
         */
        val resource: String,

        /**
         * The rate limit time window, in seconds
         * @since 2.0.0
         */
        val windowSeconds: Int,

        /**
         * The maximum number of hits allowed in within the rate limit time window
         * @since 2.0.0
         */
        val windowMaxHits: Int
    )

    private inline fun mkKey(resource: String, accessor: String) = "rl_${resource}_$accessor"

    /**
     * Records a hit on a resource by an accessor and returns whether the maximum number of hits for the specified window was reached
     * @param resource The resource identifier (e.g. "LOGIN_PAGE")
     * @param windowSeconds The rate limit time window, in seconds
     * @param windowMaxHits The maximum number of hits allowed in within the rate limit time window
     * @param accessor The accessor identifier (e.g. an IP address)
     * @return Whether the maximum number of hits for the specified window was reached
     * @since 2.0.0
     */
    suspend fun hitAndCheck(resource: String, windowSeconds: Int, windowMaxHits: Int, accessor: String): Boolean {
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
     * Records a hit on a resource by an accessor and returns whether the maximum number of hits for the specified window was reached
     * @param preset The rate limit preset to use
     * @param accessor The accessor identifier (e.g. an IP address)
     * @return Whether the maximum number of hits for the specified window was reached
     * @since 2.0.0
     */
    suspend fun hitAndCheck(preset: Preset, accessor: String): Boolean {
        return hitAndCheck(preset.resource, preset.windowSeconds, preset.windowMaxHits, accessor)
    }

    /**
     * Returns whether the maximum number of hits was reached.
     * Usually you should use [hitAndCheck] because it automatically records a hit.
     * Use this method only for operations that require you to not modify rate limit state.
     * @param resource The resource identifier (e.g. "LOGIN_PAGE")
     * @param windowMaxHits The maximum number of hits allowed
     * @param accessor The accessor identifier (e.g. an IP address)
     * @return Whether the maximum number of hits was reached.
     * @since 2.0.0
     */
    suspend fun check(resource: String, windowMaxHits: Int, accessor: String): Boolean {
        val hits = redisService.getInt(mkKey(resource, accessor))

        return hits != null && hits >= windowMaxHits
    }

    /**
     * Returns whether the maximum number of hits was reached.
     * Usually you should use [hitAndCheck] because it automatically records a hit.
     * Use this method only for operations that require you to not modify rate limit state.
     * @param preset The rate limit preset to use
     * @param accessor The accessor identifier (e.g. an IP address)
     * @return Whether the maximum number of hits was reached.
     * @since 2.0.0
     */
    suspend fun check(preset: Preset, accessor: String): Boolean {
        return check(preset.resource, preset.windowMaxHits, accessor)
    }

    /**
     * Rate limits a request by recording a hit on a resource, and return a "rate_limited" error if the maximum number of hits for the specified window was reached.
     * Returns an error response, or null if the limit was not reached.
     * If an error is returned, your controller should immediately return it, because headers are set on the request when an error is returned.
     * @param ctx The request [RoutingContext]
     * @param resource The resource identifier (e.g. "LOGIN_PAGE")
     * @param windowSeconds The rate limit time window, in seconds
     * @param windowMaxHits The maximum number of hits allowed in within the rate limit time window
     * @param accessor The accessor identifier (e.g. an IP address, defaults to the request's IP address)
     * @return An error response, or null if the limit was not reached
     * @since 2.0.0
     */
    suspend fun rateLimitRequest(ctx: RoutingContext, resource: String, windowSeconds: Int, windowMaxHits: Int, accessor: String = ctx.ip()): ApiErrorResponse? {
        return if (hitAndCheck(resource, windowSeconds, windowMaxHits, accessor)) {
            ctx.response().putHeader("Retry-After", windowSeconds.toString())
            return apiError("rate_limited", "Rate limited", statusCode = 429)
        } else {
            null
        }
    }

    /**
     * Rate limits a request by recording a hit on a resource, and return a "rate_limited" error if the maximum number of hits for the specified window was reached.
     * Returns an error response, or null if the limit was not reached.
     * If an error is returned, your controller should immediately return it, because headers are set on the request when an error is returned.
     * @param ctx The request [RoutingContext]
     * @param preset The rate limit preset to use
     * @param accessor The accessor identifier (e.g. an IP address, defaults to the request's IP address)
     * @return An error response, or null if the limit was not reached
     * @since 2.0.0
     */
    suspend fun rateLimitRequest(ctx: RoutingContext, preset: Preset, accessor: String = ctx.ip()): ApiErrorResponse? {
        return rateLimitRequest(ctx, preset.resource, preset.windowSeconds, preset.windowMaxHits, accessor)
    }
}
