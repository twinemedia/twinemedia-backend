package net.termer.twinemedia.source.config

/**
 * Exception to be thrown when a JSON is provided for configuration but does not adhere to the required schema
 * @param msg The exception message
 * @author termer
 * @since 1.5.0
 */
class ValidationFailedException(msg: String): Exception(msg)