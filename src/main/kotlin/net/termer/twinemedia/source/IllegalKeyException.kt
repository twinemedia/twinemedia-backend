package net.termer.twinemedia.source

/**
 * Exception to be thrown when a key has illegal characters or form
 * @param msg The exception message
 * @author termer
 * @since 1.5.0
 */
class IllegalKeyException(msg: String): MediaSourceException(msg)