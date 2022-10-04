package net.termer.twinemedia.model.pagination

/**
 * An exception to be thrown when an error occurred relating to pagination
 * @since 2.0.0
 */
open class PaginationException(message: String, cause: Throwable? = null): Exception(message, cause)