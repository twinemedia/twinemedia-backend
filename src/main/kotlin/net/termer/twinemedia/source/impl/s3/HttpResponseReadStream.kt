package net.termer.twinemedia.source.impl.s3

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpConnection
import io.vertx.core.streams.ReadStream
import net.termer.twinemedia.source.CloseableReadStream


/**
 * Wraps around an HttpClientResponse and HttpConnection, and implements [CloseableReadStream] by called [HttpConnection.close].
 * Should only be used on HTTP/1.x connections, since HTTP/2 may have multiple requests going through the same underlying connection.
 * @author termer
 * @since 1.5.2
 */
open class HttpResponseReadStream(val response: HttpClientResponse, val connection: HttpConnection): CloseableReadStream<Buffer> {
	private var exceptionHandler: Handler<Throwable>? = null
	private var endHandler: Handler<Void>? = null

	override fun handler(hdlr: Handler<Buffer>?): ReadStream<Buffer> {
		response.handler(hdlr)
		return this
	}
	override fun exceptionHandler(hdlr: Handler<Throwable>): ReadStream<Buffer> {
		exceptionHandler = hdlr
		response.exceptionHandler(exceptionHandler)
		return this
	}
	override fun endHandler(hdlr: Handler<Void>): ReadStream<Buffer> {
		endHandler = hdlr
		response.endHandler(endHandler)
		return this
	}

	override fun close(): Future<Void> = Future.future { promise ->
		connection.close()
				.onSuccess {
					response.end()
					promise.complete()
				}
				.onFailure {
					promise.fail(it)
					exceptionHandler?.handle(it)
				}
	}

	override fun pause(): ReadStream<Buffer> {
		response.pause()
		return this
	}
	override fun resume(): ReadStream<Buffer> {
		response.resume()
		return this
	}

	override fun fetch(amount: Long): ReadStream<Buffer> {
		response.fetch(amount)
		return this
	}
}