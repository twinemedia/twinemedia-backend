package net.termer.twinemedia.exception

/**
 * Exception to be thrown when a list-related error occurs
 * @since 1.5.2
 */
open class ListException: Exception {
	constructor(msg: String?): super(msg)
	constructor(msg: String?, cause: Throwable?): super(msg, cause)
}