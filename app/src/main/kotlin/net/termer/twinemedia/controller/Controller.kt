package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import net.termer.krestx.api.util.ApiResponse

/**
 * Interface to be implemented by API controllers.
 * Note that controllers are instantiated once per request.
 * @since 2.0.0
 */
interface Controller {
    /**
     * The controller instance's [RoutingContext].
     * Should be included in the constructor.
     * @since 2.0.0
     */
    val ctx: RoutingContext

    /**
     * Initializes the controller.
     * If a non-null [ApiResponse] is returned, execution will halt and the [ApiResponse] will be returned.
     * @since 2.0.0
     */
    suspend fun initialize(): ApiResponse?
}
