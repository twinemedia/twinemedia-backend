package net.termer.twinemedia.util

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import kotlin.random.Random

/**
 * Trims a String down to the specified length
 * @return The String trimmed down to the specified length
 * @since 1.0.0
 */
fun String.toLength(len: Int) = if(this.length > len) this.substring(0, len) else this

/**
 * Returns null if this String is empty (or is already null), otherwise returns the String
 * @return Null if this String is empty (or is already null), otherwise this String
 * @since 1.0.0
 */
fun String?.nullIfEmpty() = this?.ifEmpty { null }

/**
 * Strips a trailing forward slash from a String if present and returns the processed String
 * @return The processed String
 * @since 1.5.2
 */
fun String.stripTrailingSlash() = if(this.endsWith('/')) this.substring(0, this.length-1) else this

/**
 * Returns whether the String starts with one of the specified chars
 * @param chars The chars to check
 * @return Whether the String starts with one of the specified chars
 * @since 2.0.0
 */
fun String.startsWithOneOfChars(chars: Iterable<Char>): Boolean {
	if(isEmpty())
		return false

	// Iterate over chars and check them against first char
	for(char in chars)
		if(this[0] == char)
			return true

	return false
}

/**
 * Returns whether the String starts with one of the specified substrings
 * @param substrings The substrings to check
 * @return Whether the String starts with one of the specified substrings
 * @since 2.0.0
 */
fun String.startsWithOneOf(substrings: Iterable<String>): Boolean {
	// Iterate over substrings and check them against the string
	for(str in substrings)
		if(startsWith(str))
			return true

	return false
}

/**
 * Returns whether the String ends with one of the specified chars
 * @param chars The chars to check
 * @return Whether the String ends with one of the specified chars
 * @since 2.0.0
 */
fun String.endsWithOneOfChars(chars: Iterable<Char>): Boolean {
	if(isEmpty())
		return false

	// Iterate over chars and check them against last char
	for(char in chars)
		if(this[this.length-1] == char)
			return true

	return false
}

/**
 * Returns whether the String ends with one of the specified substrings
 * @param substrings The substrings to check
 * @return Whether the String ends with one of the specified substrings
 * @since 2.0.0
 */
fun String.endsWithOneOf(substrings: Iterable<String>): Boolean {
	// Iterate over substrings and check them against the string
	for(str in substrings)
		if(endsWith(str))
			return true

	return false
}

/**
 * Serializes this String into a [JsonObject] and returns it
 * @return The serialized result
 * @throws DecodeException If the String does not represent a valid JSON object
 */
@Throws(DecodeException::class)
fun String.toJsonObject() = JsonObject(this)

/**
 * Serializes this String into a [JsonArray] and returns it
 * @return The serialized result
 * @throws DecodeException If the String does not represent a valid JSON array
 */
@Throws(DecodeException::class)
fun String.toJsonArray() = JsonArray(this)

/**
 * Generates a String of the specified length using the randomly selected chars from the provided char array
 * @param chars The chars to use for generating the String
 * @param len The desired output String length
 * @since 2.0.0
 */
fun genStrOf(chars: Array<Char>, len: Int): String {
	val res = StringBuilder(len)

	for(i in 0 until len)
		res.append(chars[Random.nextInt(0, chars.size)])

	return res.toString()
}

/**
 * Generates a String of the specified length using the randomly selected chars from the provided char array
 * @param chars The chars to use for generating the String
 * @param len The desired output String length
 * @since 2.0.0
 */
fun genStrOf(chars: CharSequence, len: Int): String {
	val res = StringBuilder(len)

	for(i in 0 until len)
		res.append(chars[Random.nextInt(0, chars.length)])

	return res.toString()
}

/**
 * Returns only the chars from this String that are included in the provided [CharSequence]
 * @return Only the chars from this String that are included in the provided [CharSequence]
 * @since 2.0.0
 */
fun String.withOnlyChars(chars: CharSequence): String {
	val res = StringBuilder()

	for(char in this)
		if(chars.contains(char))
			res.append(char)

	return res.toString()
}