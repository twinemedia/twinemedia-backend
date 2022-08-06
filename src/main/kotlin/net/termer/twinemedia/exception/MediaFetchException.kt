package net.termer.twinemedia.exception

/**
 * Exception to be thrown when a media entry cannot be fetched
 * @since 1.5.2
 */
class MediaFetchException: MediaException {
	constructor(msg: String?): super(msg)
	constructor(msg: String?, cause: Throwable?): super(msg, cause)
}