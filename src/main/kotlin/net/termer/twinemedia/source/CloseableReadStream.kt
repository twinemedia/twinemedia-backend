package net.termer.twinemedia.source

import io.vertx.core.Future
import io.vertx.core.streams.ReadStream

/**
 * ReadStream that can be closed
 * @author termer
 * @since 1.5.0
 */
interface CloseableReadStream<T>: ReadStream<T> {
	/**
	 * Closes the stream
	 */
	fun close(): Future<Void>
}