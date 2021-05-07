package net.termer.twinemedia.sockjs

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.sockjs.SockJSSocket
import io.vertx.kotlin.coroutines.await
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.db.dataobject.Account
import java.time.OffsetDateTime

/**
 * Authenticated SockJS client class
 * @author termer
 * @since 1.5.0
 */
class SockJSClient(
		/**
		 * The manager this client belongs to
		 * @since 1.5.0
		 */
		val manager: SockJSManager,
		/**
		 * The underlying socket for this client
		 * @since 1.5.0
		 */
		val socket: SockJSSocket,
		/**
		 * The client's account
		 * @since 1.5.0
		 */
		val account: Account,
		/**
		 * The time the client's token expires and will be disconnected
		 * @since 1.5.0
		 */
		val expireTime: OffsetDateTime
) {
	/**
	 * This client's unique ID
	 * @since 1.5.0
	 */
	val id: String = generateString(10)

	/**
	 * Disconnects the client and removes it from its manager
	 * @param statusCode The status code to send to the socket before disconnecting
	 * @param statusMessage The status message to send to the socket before disconnecting
	 * @since 1.5.0
	 */
	fun disconnect(statusCode: Int, statusMessage: String) {
		socket.close(statusCode, statusMessage)
		manager.removeClient(id)
	}
	/**
	 * Disconnects the client and removes it from its manager
	 * @since 1.5.0
	 */
	fun disconnect() {
		socket.close()
		manager.removeClient(id)
	}

	/**
	 * Sends an event and additional JSON encoded data to the client
	 * @param eventType The event type
	 * @param json The JSON encoded body to send
	 * @return A future that will be fulfilled once the event is sent or fails
	 * @since 1.5.0
	 */
	fun sendEvent(eventType: String, json: JsonObject = JsonObject()): Future<Void> {
		json.put("type", eventType)
		return socket.write(json.toBuffer())
	}
}