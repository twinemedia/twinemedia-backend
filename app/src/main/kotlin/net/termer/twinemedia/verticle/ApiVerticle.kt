package net.termer.twinemedia.verticle

import io.vertx.kotlin.coroutines.CoroutineVerticle
import net.termer.twinemedia.AppContext
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

	override suspend fun start() {
		context = AppContext.fromJson(config.getJsonObject("context"))
	}

	override suspend fun stop() {

	}
}