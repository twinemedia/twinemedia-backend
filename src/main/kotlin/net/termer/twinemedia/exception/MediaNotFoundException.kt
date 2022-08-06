package net.termer.twinemedia.exception

/**
 * Exception to be thrown when a media entry cannot be found
 * @since 1.0
 */
class MediaNotFoundException: MediaException {
	constructor(msg: String?): super(msg)
	constructor(msg: String?, cause: Throwable?): super(msg, cause) {}
}