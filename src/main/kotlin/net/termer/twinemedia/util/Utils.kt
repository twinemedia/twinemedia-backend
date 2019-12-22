package net.termer.twinemedia.util

/**
 * Trims a String down to the specified length
 * @since 1.0
 */
fun String.toLength(len : Int) = if (this.length > len) this.substring(0, len) else this