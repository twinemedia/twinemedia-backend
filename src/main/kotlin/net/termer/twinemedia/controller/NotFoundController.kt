package net.termer.twinemedia.controller

import io.vertx.core.http.HttpMethod
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.router
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.error

/**
 * Not found handler for API routes
 * @since 1.4.0
 */
@DelicateCoroutinesApi
fun notFoundController() {
	for(hostname in appHostnames()) {
		// Handle all /api/ routes on the configured domain
		router().route("/api/*").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				// Send 200 if OPTIONS, otherwise send 404 and error
				if(r.request().method() == HttpMethod.OPTIONS) {
					r.response()
							.setStatusCode(200)
							.end()
				} else {
					r.response().statusCode = 404
					r.error("Not found")
				}
			}
		}
	}
}