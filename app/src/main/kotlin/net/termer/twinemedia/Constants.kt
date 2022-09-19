package net.termer.twinemedia

/**
 * Application constants
 * @since 1.4.1
 */
object Constants {
	/**
	 * The application name
	 * @since 2.0.0
	 */
	const val APP_NAME = "TwineMedia"

	/**
	 * The application version
	 * @since 1.4.1
	 */
	const val VERSION = "2.0.0"

	/**
	 * The application's supported API versions
	 * @since 1.4.1
	 */
	val API_VERSIONS = arrayOf("v2")

	/**
	 * Lowercase letters from a-z
	 * @since 2.0.0
	 */
	const val LOWERCASE_LETTER_CHARS = "abcdefghijklmnopqrstuvwxyz"

	/**
	 * Uppercase letters from A-Z
	 * @since 2.0.0
	 */
	const val UPPERCASE_LETTER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

	/**
	 * Lowercase and uppercase letters from a-Z
	 * @since 2.0.0
	 */
	const val ALL_CASE_LETTER_CHARS = LOWERCASE_LETTER_CHARS+UPPERCASE_LETTER_CHARS

	/**
	 * Number characters from 0-9
	 * @since 2.0.0
	 */
	const val NUMBER_CHARS = "0123456789"

	/**
	 * Characters considered as "special characters" for purposes such as password strength evaluation.
	 * Derived from standard special characters on a QWERTY keyboard.
	 * @since 2.0.0
	 */
	const val SPECIAL_CHARS = "`~!@#$%^&*()_-=+\\|;:'\",<.>/?[{]}"

	/**
	 * Alphanumeric characters (uppercase and lowercase) from "a-9"
	 */
	const val ALPHANUMERIC_CHARS = ALL_CASE_LETTER_CHARS+NUMBER_CHARS

	/**
	 * Characters that are not allowed in passwords in any circumstance
	 * @since 2.0.0
	 */
	const val PASSWORD_INVALID_CHARS = "\n\t\r"
}