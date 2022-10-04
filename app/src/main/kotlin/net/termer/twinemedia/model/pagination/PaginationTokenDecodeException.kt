package net.termer.twinemedia.model.pagination

/**
 * An exception thrown when a pagination token could not be decoded
 * @since 2.0.0
 */
class PaginationTokenDecodeException(message: String = "Malformed pagination token", cause: Throwable? = null): PaginationException(message, cause)