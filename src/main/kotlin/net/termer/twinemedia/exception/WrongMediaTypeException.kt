package net.termer.twinemedia.exception

/**
 * Exception to be thrown when a media file is not the desired type
 * @since 1.0
 */
class WrongMediaTypeException: MediaException {
	constructor(msg: String?): super(msg)
	constructor(msg: String?, cause: Throwable?): super(msg, cause)
}