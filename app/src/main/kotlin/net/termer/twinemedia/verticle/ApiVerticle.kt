package net.termer.twinemedia.verticle

import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.openapi.RouterBuilder
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import net.termer.krestx.api.util.*
import net.termer.twinemedia.AppContext
import net.termer.twinemedia.Constants.API_VERSIONS
import net.termer.twinemedia.Constants.CURRENT_API_VERSION
import net.termer.twinemedia.controller.AuthController
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * API verticle, setups up API routes, controllers, etc. and starts an HTTP server
 * @since 2.0.0
 * @author termer
 */
class ApiVerticle: CoroutineVerticle() {
	private val logger: Logger = LoggerFactory.getLogger(ApiVerticle::class.java)
	private lateinit var context: AppContext
	private lateinit var http: HttpServer

	override suspend fun start() {
		context = AppContext.fromJson(config.getJsonObject("context"))

		// Create main app router
		val router = Router.router(vertx)
			.defaultApiInfoHandler(CURRENT_API_VERSION, API_VERSIONS)
			.defaultApiNotFoundHandler()
			.defaultBadRequestHandler()
			.defaultMethodNotAllowedHandler()
			.defaultApiUnauthorizedHandler()
			.defaultApiInternalErrorHandler { ctx ->
				val req = ctx.request()
				logger.error("Error occurred while serving ${req.method()} ${req.path()}", ctx.failure())
			}

		// Build OpenAPI router
		val oapiRouter = RouterBuilder.create(vertx, "openapi/twinemedia.json").await()

		// Bind operations
		oapiRouter.operation("postAuth").handler(wrapApiRequestHandler {
			val controller = AuthController(it)
			controller.initialize() ?: controller.auth()
		})

		router.mountApiRouter(CURRENT_API_VERSION, oapiRouter.createRouter())

		// Create server
		val cfg = context.config
		http = vertx
			.createHttpServer()
			.requestHandler(router)
			.listen(cfg.bindPort, cfg.bindHost).await()
	}

	override suspend fun stop() {
		http.close().await()
	}
}