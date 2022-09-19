package net.termer.twinemedia.source

/**
 * Exception thrown when a write operation is attempted on a file that already exists
 * @param msg The exception message
 * @author termer
 * @since 1.5.0
 */
class FileSourceFileAlreadyExistsException(msg: String): FileSourceException(msg)