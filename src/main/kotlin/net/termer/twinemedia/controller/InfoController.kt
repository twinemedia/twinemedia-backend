package net.termer.twinemedia.controller

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Constants
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.success
import net.termer.twinemedia.util.toJsonArray

/**
 * Sets up routes that return info about the API and server
 * @since 1.4.1
 */
fun infoController() {
	for(hostname in appHostnames()) {
		// Returns info about the API and server
		ServerManager.router().get("/api/v1/info").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				r.success(json {obj(
						"version" to Constants.VERSION,
						"api_versions" to Constants.API_VERSIONS.toJsonArray()
				)})
			}
		}
	}
}