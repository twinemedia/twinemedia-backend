package net.termer.twinemedia.source

/**
 * Exception thrown when an operation is attempted on a file that does not exist
 * @param msg The exception message
 * @author termer
 * @since 1.5.0
 */
class FileSourceFileNotFoundException(msg: String): FileSourceException(msg)