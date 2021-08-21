package net.termer.twinemedia.sockjs

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.jwt.JWT
import net.termer.twinemedia.jwt.extractJWTPrinciple
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.util.appHostnames
import net.termer.twinemedia.util.userId
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Manager class for SockJS connections and messaging
 * @author termer
 * @since 1.5.0
 */
@DelicateCoroutinesApi
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
		val res = ArrayList<SockJSClient>()

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
				val kickTimer = vertx().setTimer(10_000L) {
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
								JWT.provider?.authenticate(json {obj(
										"token" to token
								)})?.await()!!

								// Get principle
								val principal = extractJWTPrinciple(token)

								// If it got to this point, the socket is authenticated
								authed = true

								try {
									// Fetch extract expire time from principal
									val expire = if(principal.containsKey("exp"))
										Date(principal.getLong("exp")*1000).toInstant().atOffset(ZoneOffset.UTC)
									else
										null

									// Fetch account, taking into account whether it's via an API token
									val accountRes = if(principal.containsKey("token"))
										accountsModel.fetchAccountAndApiKeyByKeyId(principal.getString("token"))
									else
										accountsModel.fetchAccountById(principal.getInteger("sub"))
									if(accountRes.size() > 0) {
										val account = accountRes.first()

										// Create client and add it
										client = SockJSClient(manager, false, sock, account, expire)
										clients[client!!.id] = client!!

										// Broadcast connect event
										vertx().eventBus().publish("twinemedia.socket.event.connect", json {obj(
												"id" to client!!.id,
												"account" to account.id,
												"expire" to expire?.toString()
										)})

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

						// Broadcast disconnect event
						vertx().eventBus().publish("twinemedia.socket.event.disconnect", json {obj(
								"id" to client!!.id
						)})

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
		vertx().setPeriodic(60_000L) {
			// Current time
			val now = Date().toInstant().atOffset(ZoneOffset.UTC)

			// Loop through connected clients and disconnect those past their expiration time
			for(client in clients.values) {
				if(client.expireTime != null && now.isAfter(client.expireTime))
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

			// Send to clients which can receive this event (and which aren't virtual)
			val clients = getClients()
			for(client in clients) {
				if(!client.isVirtual && (client.account.id == creator || client.account.hasPermission("files.view.all")))
					client.sendEvent("media_process_progress", event)
			}
		}

		// Handle events directed at specific accounts
		vertx().eventBus().consumer<JsonObject>("twinemedia.event.account") { msg ->
			val body = msg.body()
			val account = body.getInteger("account")
			val type = body.getString("type")
			val json = body.getJsonObject("json")

			val clients = getClients()

			for(client in clients)
				if(!client.isVirtual && client.account.id == account)
					client.sendEvent(type, json)
		}
		// Handle events directed at all accounts
		vertx().eventBus().consumer<JsonObject>("twinemedia.event") { msg ->
			val body = msg.body()
			val type = body.getString("type")
			val json = body.getJsonObject("json")

			val clients = getClients()

			for(client in clients)
				if(!client.isVirtual)
					client.sendEvent(type, json)
		}

		// Handle socket connect and disconnect events
		vertx().eventBus().consumer<JsonObject>("twinemedia.socket.event.connect") { msg ->
			val body = msg.body()
			val id = body.getString("id")
			val accountId = body.getInteger("account")
			val expireStr = body.getString("expire")
			val expireTime = if(expireStr == null)
				null
			else
				OffsetDateTime.parse(expireStr)

			// Check if the client is already connected here
			if(getClientById(id) != null) {
				return@consumer
			}

			// Fetch account
			GlobalScope.launch(vertx().dispatcher()) {
				try {
					// Fetch account
					val accountRes = accountsModel.fetchAccountById(accountId)

					// Check if it exists
					if(accountRes.rowCount() < 1) {
						// It doesn't exist, nothing to be done
						return@launch
					}

					val account = accountRes.first()

					// Create virtual client
					clients[id] = SockJSClient(manager, true, null, account, expireTime, id)
				} catch(e: Exception) {
					logger.error("Failed to fetch account ID $accountId while handling twinemedia.socket.event.connect event:")
					e.printStackTrace()
				}
			}
		}
		vertx().eventBus().consumer<JsonObject>("twinemedia.socket.event.disconnect") { msg ->
			val body = msg.body()
			val id = body.getString("id")

			// Check if the client exists in the client list
			getClientById(id) ?: return@consumer

			removeClient(id)
		}

		// Handle socket commands from other instances
		vertx().eventBus().consumer<JsonObject>("twinemedia.socket.command.disconnect") { msg ->
			val body = msg.body()
			val id = body.getString("id")
			val statusCode: Int? = body.getInteger("status_code")
			val statusMessage: String? = body.getString("status_message")

			// Check if client is connected and not virtual
			val client = getClientById(id) ?: return@consumer
			if(!client.isVirtual) {
				// Disconnect client
				if(statusCode == null || statusMessage == null) {
					client.disconnect()
				} else {
					client.disconnect(statusCode, statusMessage)
				}

				msg.reply(json {obj(
						"status" to "success"
				)})
			}
		}
		vertx().eventBus().consumer<JsonObject>("twinemedia.socket.command.event") { msg ->
			val body = msg.body()
			val id = body.getString("id")
			val type = body.getString("type")
			val json = body.getJsonObject("json")

			// Check if client is connected and not virtual
			val client = getClientById(id) ?: return@consumer
			if(!client.isVirtual) {
				client.sendEvent(type, json).onComplete {
					if(it.succeeded()) {
						msg.reply(null)
					} else {
						logger.error("Failed to send event to SockJSClient ID $id:")
						it.cause().printStackTrace()

						msg.fail(500, "internal_error")
					}
				}
			}
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
		vertx().eventBus().publish("twinemedia.event", json {obj(
				"type" to eventType,
				"json" to json
		)})
	}
	/**
	 * Broadcasts an event to all connected clients with the specified account ID
	 * @param accountId The account ID
	 * @param eventType The event type
	 * @param json The JSON encoded body to send
	 * @since 1.5.0
	 */
	fun broadcastEventByAccountId(accountId: Int, eventType: String, json: JsonObject = JsonObject()) {
		vertx().eventBus().publish("twinemedia.event.account", json {obj(
				"account" to accountId,
				"type" to eventType,
				"json" to json
		)})
	}

	/**
	 * Sends an event to the specified client
	 * @param clientId The client's ID
	 * @param eventType The type of event to send
	 * @param json The event's JSON body
	 * @return A Future that is resolved when the event either succeeded in sending or failed
	 * @since 1.5.0
	 */
	fun sendEventToClient(clientId: String, eventType: String, json: JsonObject = JsonObject()): Future<Void> {
		val client = getClientById(clientId)

		if(client == null || client.isVirtual) {
			return Future.future { promise ->
				// Send event request
				vertx().eventBus().request<Void>("twinemedia.socket.command.event", json {obj(
						"id" to clientId,
						"type" to eventType,
						"json" to json
				)}).onComplete {
					if(it.succeeded())
						promise.complete()
					else
						promise.fail(it.cause())
				}
			}
		} else {
			// Compose event content
			val buf = json
					.copy()
					.put("type", eventType)
					.toBuffer()

			// Send event
			return client.socket!!.write(buf)
		}
	}

	/**
	 * Disconnects the specified client
	 * @param clientId The client's ID
	 * @param statusCode The status code to send along with the disconnection
	 * @param statusMessage The status message to send along with the disconnection
	 * @return A Future that is resolved when the disconnection either succeeded or failed
	 * @since 1.5.0
	 */
	fun disconnectClient(clientId: String, statusCode: Int? = null, statusMessage: String? = null): Future<Void> = Future.future { promise ->
		val client = getClientById(clientId)
		removeClient(clientId)

		if(client == null || client.isVirtual) {
			// Send event request
			vertx().eventBus().request<Void>("twinemedia.socket.command.disconnect", json {obj(
					"id" to clientId,
					"status_code" to statusCode,
					"status_message" to statusMessage
			)}).onComplete {
				if(it.succeeded())
					promise.complete()
				else
					promise.fail(it.cause())
			}
		} else {
			// Disconnect
			if(statusCode == null || statusMessage == null)
				client.socket!!.close()
			else
				client.socket!!.close(statusCode, statusMessage)

			promise.complete()
		}
	}
}