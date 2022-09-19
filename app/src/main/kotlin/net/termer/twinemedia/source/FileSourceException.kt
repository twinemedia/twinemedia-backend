package net.termer.twinemedia.source

/**
 * Class for exceptions relating to file sources
 * @param msg The exception message
 * @param cause The Throwable that caused this [FileSourceException] to be thrown, or null if none (defaults to null)
 * @author termer
 * @since 1.5.0
 */
open class FileSourceException(msg: String, override val cause: Throwable? = null): Exception(msg)