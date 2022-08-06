package net.termer.twinemedia.exception

/**
 * Exception to be thrown when a list cannot be deleted
 * @since 1.5.2
 */
class ListDeleteException: ListException {
	constructor(msg: String?): super(msg)
	constructor(msg: String?, cause: Throwable?): super(msg, cause)
}