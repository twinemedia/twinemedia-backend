package net.termer.twinemedia.sockjs

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.sockjs.SockJSSocket
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.db.dataobject.Account
import java.time.OffsetDateTime

/**
 * Authenticated SockJS client class
 * @author termer
 * @since 1.5.0
 */
@DelicateCoroutinesApi
class SockJSClient(
		/**
		 * The manager this client belongs to
		 * @since 1.5.0
		 */
		val manager: SockJSManager,

		/**
		 * Whether this is a "virtual client".
		 * Virtual clients are references to actual clients that are connected to another instance.
		 * Methods on virtual clients are translated into commands to are sent to the instance with the actual client connection.
		 * @since 1.5.0
		 */
		val isVirtual: Boolean,

		/**
		 * The underlying socket for this client (or null if this is a virtual client)
		 * @since 1.5.0
		 */
		val socket: SockJSSocket?,

		/**
		 * The client's account
		 * @since 1.5.0
		 */
		val account: Account,

		/**
		 * The time the client's token expires and will be disconnected
		 * @since 1.5.0
		 */
		val expireTime: OffsetDateTime?,

		/**
		 * This client's unique ID
		 * @since 1.5.0
		 */
		val id: String = generateString(10)
) {
	/**
	 * Disconnects the client and removes it from its manager
	 * @param statusCode The status code to send to the socket before disconnecting
	 * @param statusMessage The status message to send to the socket before disconnecting
	 * @return A Future that will be fulfilled once the disconnection succeeds or fails
	 * @since 1.5.0
	 */
	fun disconnect(statusCode: Int? = null, statusMessage: String? = null) = manager.disconnectClient(this.id, statusCode, statusMessage)

	/**
	 * Sends an event and additional JSON encoded data to the client
	 * @param eventType The event type
	 * @param json The JSON encoded body to send
	 * @return A Future that will be fulfilled once the event is sent or fails
	 * @since 1.5.0
	 */
	fun sendEvent(eventType: String, json: JsonObject = JsonObject()) = manager.sendEventToClient(this.id, eventType, json)
}