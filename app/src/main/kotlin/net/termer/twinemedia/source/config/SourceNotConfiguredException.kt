package net.termer.twinemedia.source.config

/**
 * Exception to be thrown when an operation is called on a file source that hasn't been configured
 * @param msg The exception message
 * @author termer
 * @since 1.5.0
 */
class SourceNotConfiguredException(msg: String): Exception(msg)