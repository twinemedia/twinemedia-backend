package net.termer.twinemedia.util

import net.termer.twinemedia.source.FileSourceException

// Strings that a file key cannot contain
private val illegalKeyStrs = arrayOf("\r", "\n", "--", "..", "#", "%", "&", "{", "}", "\\", "<", ">", "*", "?", "$", "!", "'", "\"", ":", "@", "+", "`", "|", "=")

/**
 * Strips invalid characters (and character sequences) from a file key and returns the stripped version.
 * See documentation for [isFileKeyValid] to see invalid characters and sequences.
 * @param key The key to strip
 * @return The stripped key
 * @since 1.5.0
 */
fun stripInvalidFileKeyChars(key: String): String {
	var res = key

	for(str in illegalKeyStrs)
		res = res.replace(str, "")

	return res
}

/**
 * Returns whether the provided file key contains any invalid characters or character sequences.
 * Illegal characters and sequences are as follows:
 * \r (carriage return), \n (newline), --, .., #, %, &, {, }, \, <, >, *, ?, $, !, ', ", :, @, +, `, |, =
 * Additionally, keys cannot start or end with /, or end with ., and they also cannot be blank
 * @param key The file key to check
 * @return Whether the provided file key contains any invalid characters or character sequences
 * @since 1.5.0
 */
fun isFileKeyValid(key: String): Boolean {
	if(key.isEmpty())
		return false

	// Check if it starts or ends with a slash
	if(key.startsWith('/') || key.endsWith('/'))
		return false

	// Check if it ends with a dot
	if(key.endsWith('.'))
		return false

	// Check for illegal strings
	for(str in illegalKeyStrs)
		if(key.contains(str))
			return false

	return true
}

/**
 * Wraps a Throwable as a [FileSourceException] and returns it
 * @param e The Throwable to wrap
 * @return The wrapped throwable
 * @since 1.5.0
 */
fun wrapThrowableAsFileSourceException(e: Throwable) = if(e is FileSourceException)
	e
else
	FileSourceException("${e.javaClass.name}: ${e.message}", e)