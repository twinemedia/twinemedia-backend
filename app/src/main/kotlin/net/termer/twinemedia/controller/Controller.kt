package net.termer.twinemedia.controller

import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.RequestParameter
import io.vertx.ext.web.validation.RequestParameters
import io.vertx.ext.web.validation.ValidationHandler
import net.termer.krestx.api.util.ApiResponse
import net.termer.twinemedia.AppContext

/**
 * Abstract class to be extended by API controllers.
 * Note that controllers are instantiated once per request.
 * @since 2.0.0
 */
abstract class Controller {
    /**
     * The application context
     * @since 2.0.0
     */
    protected abstract val appCtx: AppContext

    /**
     * The controller instance's [RoutingContext].
     * Should be included in the constructor.
     * @since 2.0.0
     */
    protected abstract val ctx: RoutingContext

    /**
     * The request's parameters from the OpenAPI validator
     * @since 2.0.0
     */
    protected val params: RequestParameters by lazy { ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY) }

    /**
     * The request's body parameters from the OpenAPI validator
     * @since 2.0.0
     */
    protected val bodyParams: RequestParameter by lazy { params.body() }

    /**
     * Initializes the controller.
     * If a non-null [ApiResponse] is returned, execution will halt and the [ApiResponse] will be returned.
     * @since 2.0.0
     */
    abstract suspend fun initialize(): ApiResponse?
}
