package net.termer.twinemedia.source.impl.s3

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.async.SdkPublisher
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture


/**
 * Reads a T stream and converts it to a ReadStream
 * @author termer
 * @since 1.5.0
 */
open class ReadStreamAsyncResponseTransformer<T>: AsyncResponseTransformer<T, ReadStream<Buffer>>, ReadStream<Buffer> {
	@Volatile
	private var cf: CompletableFuture<ReadStream<Buffer>>? = null

	@Volatile
	protected var responseHandler: Handler<T>? = null

	// ReadStream vars
	private var handler: Handler<Buffer>? = null
	private var exceptionHandler: Handler<Throwable>? = null
	private var endHandler: Handler<Void>? = null
	private var paused = false
	private var ended = false

	// Subscription related variables
	private var waitingFor = 0L
	private val queue = ArrayList<Buffer>()
	private var subscription: Subscription? = null

	private val subscriber = object: Subscriber<ByteBuffer> {
		val bufSize = 1024L

		override fun onSubscribe(s: Subscription) {
			subscription = s
			s.request(bufSize)
		}

		override fun onNext(t: ByteBuffer) {
			try {
				// Deal with buffer if paused, otherwise just send it to handler
				if(paused) {
					// Handle buffer immediately if packets are being waited for, otherwise store in queue
					if(waitingFor > 0) {
						handler?.handle(Buffer.buffer(t.array()))
						waitingFor--
					} else {
						queue.add(Buffer.buffer(t.array()))
					}
				} else {
					handler?.handle(Buffer.buffer(t.array()))
					subscription?.request(bufSize)
				}
			} catch(e: Throwable) {
				exceptionHandler?.handle(e)
			}
		}

		override fun onError(t: Throwable?) {
			exceptionHandler?.handle(t)
		}

		override fun onComplete() {
			ended = true

			// End if there are no bytes in the buffer, otherwise do nothing
			if(queue.size < 1)
				endHandler?.handle(null)
		}
	}

	override fun prepare(): CompletableFuture<ReadStream<Buffer>>? {
		cf = CompletableFuture()
		return cf
	}

	override fun onResponse(response: T?) {
		responseHandler?.handle(response)
	}

	override fun onStream(publisher: SdkPublisher<ByteBuffer>) {
		publisher.subscribe(subscriber)
	}

	override fun exceptionOccurred(error: Throwable?) {
		cf!!.completeExceptionally(error)
	}

	fun setResponseHandler(handler: Handler<T>): ReadStreamAsyncResponseTransformer<T> {
		responseHandler = handler
		return this
	}

	/* ReadStream methods */
	override fun handler(hdlr: Handler<Buffer>?): ReadStream<Buffer> {
		handler = hdlr
		return this
	}
	override fun exceptionHandler(hdlr: Handler<Throwable>): ReadStream<Buffer> {
		exceptionHandler = hdlr
		return this
	}
	override fun endHandler(hdlr: Handler<Void>): ReadStream<Buffer> {
		endHandler = hdlr
		return this
	}

	override fun pause(): ReadStream<Buffer> {
		paused = true
		return this
	}
	override fun resume(): ReadStream<Buffer> {
		paused = false
		waitingFor = 0

		try {
			// Empty buffer
			while(!paused && queue.size > 0) {
				handler?.handle(queue[0])
				queue.removeAt(0)
			}

			// Determine if stream can be ended
			if(ended && queue.size < 1)
				endHandler?.handle(null)
		} catch(e: Throwable) {
			exceptionHandler?.handle(e)
		}

		return this
	}

	override fun fetch(amount: Long): ReadStream<Buffer> {
		paused = true

		try {
			// Empty queue
			var processed = 0
			while(queue.size > 0 && processed < amount) {
				handler?.handle(queue[0])
				queue.removeAt(0)
				processed++
			}

			// Determine how many buffers need to be received
			waitingFor = (amount-processed).coerceAtLeast(0)

			// Determine if stream can be ended
			if(ended && waitingFor < 1 && queue.size < 1)
				endHandler?.handle(null)
		} catch(e: Throwable) {
			exceptionHandler?.handle(e)
		}

		return this
	}
}