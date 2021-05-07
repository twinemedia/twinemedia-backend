package net.termer.twinemedia.sockjs

import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.jwt.JWT
import net.termer.twinemedia.jwt.extractJWTPrinciple
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.util.appHostnames
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Manager class for SockJS connections and messaging
 * @author termer
 * @since 1.5.0
 */
class SockJSManager {
	// Connected clients
	private val clients = HashMap<String, SockJSClient>()
	// Accounts model
	private val accountsModel = AccountsModel()
	// Connection and disconnection handlers
	private val connHandlers = ArrayList<Handler<SockJSClient>>()
	private val disconnHandlers = ArrayList<Handler<SockJSClient>>()

	/**
	 * Removes a client from the list of currently connected clients, but does not disconnect it, use client.disconnect() to do that
	 * @param id The ID of the client to remove
	 * @since 1.5.0
	 */
	fun removeClient(id: String) {
		clients.remove(id)
	}

	/**
	 * Returns the client with the specified ID, or null if none exists
	 * @param id The ID of the client to return
	 * @return The client with the specified ID
	 * @since 1.5.0
	 */
	fun getClientById(id: String): SockJSClient? {
		return clients[id]
	}

	/**
	 * Returns all connected clients with the specified account ID
	 * @param accountId The account ID of the clients to return
	 * @return All connected clients with the specified account ID
	 * @since 1.5.0
	 */
	fun getClientsByAccountId(accountId: Int): Array<SockJSClient> {
		var res = ArrayList<SockJSClient>()

		for(client in clients.values)
			if(client.account.id == accountId)
				res.add(client)

		return res.toTypedArray()
	}

	/**
	 * Returns all currently connected clients
	 * @return All currently connected clients
	 * @since 1.5.0
	 */
	fun getClients(): Array<SockJSClient> {
		return clients.values.toTypedArray()
	}

	/**
	 * Initializes the manager
	 * @since 1.5.0
	 */
	fun initialize() {
		val manager = this

		// Setup SockJS handler and routes
		for(hostname in appHostnames()) {
			val sockJSHandler = SockJSHandler.create(vertx())
			sockJSHandler.socketHandler { sock ->
				// Whether the socket has been authenticated
				var authed = false
				// The client associated with this socket
				var client: SockJSClient? = null

				// Timer that kicks off unauthenticated sockets after 10 seconds
				val kickTimer = vertx().setTimer(10*1000) {
					sock.close(403, "Did not authenticate in time")
				}

				// Handle authentication and incoming data
				sock.handler {
					GlobalScope.launch(vertx().dispatcher()) {
						// Accept token from unauthenticated sockets
						if(!authed && client == null) {
							val token = it.toString(Charsets.UTF_8)

							// Check if token matches pattern
							try {
								// Validate token
								JWT.provider?.authenticate(JsonObject().put("jwt", token))?.await()!!

								// Get principle
								val principal = extractJWTPrinciple(token)

								// If it got to this point, the socket is authenticated
								authed = true

								try {
									// Fetch extract expire time from principal
									val expire = Date(principal.getLong("exp")*1000).toInstant().atOffset(ZoneOffset.UTC)

									// Fetch account
									val accountRes = accountsModel.fetchAccountById(principal.getInteger("sub"))
									if(accountRes.size() > 0) {
										val account = accountRes.first()

										// Create client and add it
										client = SockJSClient(manager, sock, account, expire)
										clients[client!!.id] = client!!

										// Cancel kick timer
										vertx().cancelTimer(kickTimer)

										// Send authentication notice
										client!!.sendEvent("authenticated")

										// Fire connect events
										for(handler in connHandlers) {
											try {
												handler.handle(client!!)
											} catch(e: Throwable) {
												logger.error("Error occurred while firing SockJS client connection handler:")
												e.printStackTrace()
											}
										}
									} else {
										sock.close(403, "Invalid JWT token provided")
									}
								} catch(e: Exception) {
									logger.error("Failed to fetch client account from token:")
									e.printStackTrace()

									sock.close(500, "Internal error")
								}
							} catch(e: Throwable) {
								sock.close(403, "Invalid JWT token provided")
							}
						}
					}
				}

				// Handle client disconnection
				sock.endHandler {
					if(client != null) {
						removeClient(client!!.id)

						// Fire disconnect events
						for(handler in disconnHandlers) {
							try {
								handler.handle(client!!)
							} catch(e: Throwable) {
								logger.error("Error occurred while firing SockJS client disconnection handler:")
								e.printStackTrace()
							}
						}
					}
				}
			}

			// Setup route
			router().route("/api/v1/sockjs/*").virtualHost(hostname).handler(sockJSHandler)
		}

		// Disconnect clients with expired tokens
		vertx().setPeriodic(60*1000) {
			// Current time
			val now = Date().toInstant().atOffset(ZoneOffset.UTC)

			// Loop through connected clients and disconnect those past their expiration time
			for(client in clients.values) {
				if(now.isAfter(client.expireTime))
					client.disconnect()
			}
		}

		/* Handle relevant eventbus messages and send them to the correct clients */
		// Handle media processing progress
		vertx().eventBus().consumer<JsonObject>("twinemedia.event.media.process") { msg ->
			val body = msg.body()
			val creator = body.getInteger("creator")

			// Compose event
			val status = body.getString("status")
			val event = json {obj(
					"id" to body.getString("id"),
					"status" to status
			)}
			if(status == "progress")
				event.put("percent", body.getInteger("percent"))
			else if(status == "error")
				event.put("error", body.getString("error"))

			// Send to clients which can receive this event
			val clients = getClients()
			for(client in clients) {
				if(client.account.id == creator || client.account.hasPermission("files.view.all"))
					client.sendEvent("media_process_progress", event)
			}
		}

		// Handle events directed at specific accounts
		vertx().eventBus().consumer<JsonObject>("twinemedia.event.account") { msg ->
			val body = msg.body()
			val account = body.getInteger("account")
			val type = body.getString("type")
			val json = body.getJsonObject("json")

			broadcastEventByAccountId(account, type, json)
		}
		// Handle events directed at all accounts
		vertx().eventBus().consumer<JsonObject>("twinemedia.event") { msg ->
			val body = msg.body()
			val account = body.getInteger("account")
			val type = body.getString("type")
			val json = body.getJsonObject("json")

			broadcastEventByAccountId(account, type, json)
		}
	}

	/**
	 * Adds a connection handler
	 * @param handler The connection handler to add
	 * @return This, to be used fluently
	 * @since 1.5.0
	 */
	fun onConnect(handler: Handler<SockJSClient>): SockJSManager {
		connHandlers.add(handler)
		return this
	}
	/**
	 * Adds a disconnection handler
	 * @param handler The disconnection handler to add
	 * @return This, to be used fluently
	 * @since 1.5.0
	 */
	fun onDisconnect(handler: Handler<SockJSClient>): SockJSManager {
		disconnHandlers.add(handler)
		return this
	}

	/**
	 * Broadcasts an event to all currently connected clients
	 * @param eventType The event type
	 * @param json The JSON encoded body to send
	 * @since 1.5.0
	 */
	fun broadcastEvent(eventType: String, json: JsonObject = JsonObject()) {
		val clients = getClients()

		for(client in clients)
			client.sendEvent(eventType, json)
	}
	/**
	 * Broadcasts an event to all connected clients with the specified account ID
	 * @param accountId The account ID
	 * @param eventType The event type
	 * @param json The JSON encoded body to send
	 * @since 1.5.0
	 */
	fun broadcastEventByAccountId(accountId: Int, eventType: String, json: JsonObject = JsonObject()) {
		val clients = getClientsByAccountId(accountId)

		for(client in clients)
			client.sendEvent(eventType, json)
	}
}