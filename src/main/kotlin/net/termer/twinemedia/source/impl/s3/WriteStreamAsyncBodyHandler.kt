package net.termer.twinemedia.source.impl.s3

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.WriteStream
import net.termer.twinemedia.source.MediaSourceException
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import software.amazon.awssdk.core.async.AsyncRequestBody
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

/**
 *  Writes to an AWS request body by implementing a Vert.x WriteStream
 *  @author termer
 *  @since 1.5.0
 */
class WriteStreamAsyncBodyHandler: AsyncRequestBody, WriteStream<Buffer> {
	private var waitForFuture: Future<AsyncResult<Void>>? = null
	private var subscriber: Subscriber<in ByteBuffer>? = null
	private var maxQueueSize = 10
	private val queue = ArrayList<Buffer>().apply { ensureCapacity(maxQueueSize) }
	private var waiting = 0L
	private var exceptionHandler: Handler<Throwable>? = null
	private var drainHandler: Handler<Void>? = null
	private var done = false
	private var endHandler: Handler<AsyncResult<Void>>? = null

	/**
	 * Forces the stream's end method to wait until the provided Future is resolved
	 * @param future The Future to wait on
	 * @since 1.5.0
	 */
	fun waitForFuture(future: Future<AsyncResult<Void>>) {
		waitForFuture = future
	}

	private val subscription = object : Subscription {
		override fun request(n: Long) {
			if(n > 0) {
				// Handle buffered packets
				val queueSize = queue.size
				var processed = 0
				while(queue.size > 0 && processed < n) {
					val buf = queue[0]
					queue.removeAt(0)

					try {
						subscriber?.onNext(ByteBuffer.wrap(buf.bytes))
						processed++
					} catch(e: Throwable) {
						exceptionHandler?.handle(e)

						if(done)
							endHandler?.handle(Future.failedFuture(e))
					}
				}

				// Add waiting count
				waiting += (n - processed).coerceAtLeast(0)

				// Check if done and queue is empty
				if(done && queue.size < 1) {
					subscriber?.onComplete()

					if(endHandler != null)
						endHandler?.handle(Future.succeededFuture())
				} else {
					// Call drain handler if queue was previously full
					if(queueSize >= maxQueueSize)
						drainHandler?.handle(null)
				}
			}
		}

		override fun cancel() {
			waiting = 0
			queue.clear()
			done = true

			val exception = MediaSourceException("Connection closed by upstream")
			exceptionHandler?.handle(exception)

			endHandler?.handle(Future.failedFuture<Void>(exception))
		}
	}

	override fun subscribe(s: Subscriber<in ByteBuffer>) {
		subscriber = s
		s.onSubscribe(subscription)
	}

	override fun contentLength() = Optional.ofNullable<Long>(null)

	override fun exceptionHandler(handler: Handler<Throwable>?): WriteStream<Buffer> {
		exceptionHandler = handler
		return this
	}

	override fun write(data: Buffer, handler: Handler<AsyncResult<Void>>) {
		if(!writeQueueFull()) {
			// Handle data immediately if waiting, otherwise add to queue
			if(waiting > 0) {
				try {
					subscriber?.onNext(ByteBuffer.wrap(data.bytes))
					waiting--

					handler.handle(Future.succeededFuture())
				} catch(e: Throwable) {
					handler.handle(Future.failedFuture(e))
				}
			} else {
				queue.add(data)

				handler.handle(Future.succeededFuture())
			}
		}
	}

	override fun write(data: Buffer): Future<Void> {
		val promise = Promise.promise<Void>()

		write(data) {
			if(it.failed())
				promise.fail(it.cause())
			else
				promise.complete()
		}

		return promise.future()
	}

	override fun end(handler: Handler<AsyncResult<Void>>) {
		done = true

		val hdlr = Handler<AsyncResult<Void>> { res ->
			if(res.failed()) {
				handler.handle(res)
				return@Handler
			}

			if(waitForFuture != null) {
				waitForFuture!!.onFailure {
					handler.handle(Future.failedFuture(it))
				}.onSuccess {
					handler.handle(res)
				}
			} else {
				handler.handle(res)
			}
		}

		if(queue.size < 1) {
			subscriber?.onComplete()
			hdlr.handle(Future.succeededFuture())
		} else {
			endHandler = hdlr
		}
	}

	override fun setWriteQueueMaxSize(maxSize: Int): WriteStream<Buffer> {
		maxQueueSize = maxSize
		queue.ensureCapacity(maxSize)

		return this
	}

	override fun writeQueueFull() = queue.size >= maxQueueSize

	override fun drainHandler(handler: Handler<Void>): WriteStream<Buffer> {
		drainHandler = handler
		return this
	}
}