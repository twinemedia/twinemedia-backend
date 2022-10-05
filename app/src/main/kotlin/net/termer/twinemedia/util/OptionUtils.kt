package net.termer.twinemedia.util

/**
 * An optional type.
 * Not the same as [java.util.Optional] as it may contain null values.
 * @since 2.0.0
 */
sealed interface Option<T>

/**
 * Implementation of [Option] that contains a value
 * @since 2.0.0
 */
data class Some<T>(
	/**
	 * The value
	 * @since 2.0.0
	 */
	val value: T
): Option<T>

/**
 * Implementation of [Option] that does not contain a value
 * @since 2.0.0
 */
class None<T>: Option<T>

/**
 * Creates a [Some] instance with the provided value
 * @param value The value
 * @return The [Some] instance
 * @since 2.0.0
 */
fun <T> some(value: T) = Some(value)

/**
 * Creates a [None] instance
 * @return The [None] instance
 * @since 2.0.0
 */
fun <T> none() = None<T>()