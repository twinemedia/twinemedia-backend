package net.termer.twinemedia.util

import java.io.Console

/**
 * Prompts the user for a text answer via the console
 * @param message The prompt message
 * @param default The default value to use if input was empty (defaults to "")
 * @param trim Whether to trim input (trimmed before checking if empty, defaults to true)
 * @param promptUntilNonEmpty Whether to continue prompting for input until non-empty input is received (defaults to false)
 * @param disableEcho Whether to disable character echo while typing (defaults to false)
 * @return The answer if acceptable, or a default if specified
 * @since 2.0.0
 */
fun Console.promptLine(message: String, default: String = "", trim: Boolean = true, promptUntilNonEmpty: Boolean = false, disableEcho: Boolean = false): String {
	var res: String
	do {
		print(message+(if(default == "") "" else " ($default)")+": ")

		res = (if(disableEcho)
			readPassword().joinToString("")
		else
			readlnOrNull()) ?: ""

		if(trim)
			res = res.trim()

		if(res.isEmpty())
			res = default
	} while(promptUntilNonEmpty && res.isEmpty())

	return res
}

/**
 * Prompts the user for a yes/no answer via the console
 * @param message The prompt message
 * @param defaultYes Whether to default to "Yes" if empty input is received (defaults to false)
 * @return The answer, or the default if empty input is received
 * @since 2.0.0
 */
fun Console.promptYesNo(message: String, defaultYes: Boolean = false): Boolean {
	// Loop until acceptable input is received
	while(true) {
		print("$message ["+(if(defaultYes) "Y/n" else "y/N")+"]: ")
		val ln = readlnOrNull()?.trim()?.lowercase() ?: ""

		if(ln.isEmpty())
			return defaultYes

		// Accept strings beginning with 'y' for yes, and 'n' for no
		if(defaultYes && ln.startsWith('n'))
			return false
		else if(!defaultYes && ln.startsWith('y'))
			return true

		return defaultYes
	}
}

/**
 * Prompts the user for a number answer via the console
 * @param message The prompt message
 * @param default The default value to use if input was empty, or null for no default (defaults to null)
 * @return The answer, or default if specified and empty input is received
 * @since 2.0.0
 */
inline fun <reified T: Number> Console.promptNumber(message: String, default: T? = null): T {
	// Loop until acceptable input is received
	while(true) {
		val ln = promptLine(message, default?.toString() ?: "", true, default == null)

		// Try to parse line as number
		if(ln.isNotEmpty()) {
			val num = when(T::class.simpleName) {
				"Double" -> ln.toDoubleOrNull()
				"Float" -> ln.toFloatOrNull()
				"Long" -> ln.toLongOrNull()
				"Short" -> ln.toShortOrNull()
				"Byte" -> ln.toByteOrNull()
				else -> ln.toIntOrNull()
			} as T? ?: continue // If parsing failed, loop again

			return num
		}
	}
}