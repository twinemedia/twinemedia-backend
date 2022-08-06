package net.termer.twinemedia.exception

/**
 * Exception to be thrown when a media-related error occurs
 * @since 1.5.2
 */
open class MediaException: Exception {
	constructor(msg: String?): super(msg)
	constructor(msg: String?, cause: Throwable?): super(msg, cause)
}