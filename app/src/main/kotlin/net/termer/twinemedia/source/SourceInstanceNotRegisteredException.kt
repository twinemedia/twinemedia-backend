package net.termer.twinemedia.source

/**
 * Exception thrown when a source instance is referenced but was not registered
 * @param msg The exception message
 * @author termer
 * @since 1.5.2
 */
class SourceInstanceNotRegisteredException(msg: String): FileSourceException(msg)