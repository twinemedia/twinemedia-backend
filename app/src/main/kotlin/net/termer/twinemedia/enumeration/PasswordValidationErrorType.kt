package net.termer.twinemedia.enumeration

import net.termer.twinemedia.util.PASSWORD_INVALID_CHARS
import net.termer.twinemedia.util.SPECIAL_CHARS

/**
 * Types of password validation errors
 * @since 2.0.0
 */
enum class PasswordValidationErrorType {
	/**
	 * The password did not meet the minimum length requirement
	 * @since 2.0.0
	 */
	TOO_SHORT,

	/**
	 * The password contains characters that are not allowed.
	 * Invalid characters can be found in the [PASSWORD_INVALID_CHARS] constant.
	 * @since 2.0.0
	 */
	INVALID_CHARS,

	/**
	 * The password does not have an uppercase character
	 * @since 2.0.0
	 */
	MISSING_UPPERCASE,

	/**
	 * The password does not have a numeric character (0-9)
	 * @since 2.0.0
	 */
	MISSING_NUMBER,

	/**
	 * The password does not have a special character.
	 * Special characters can be found in the [SPECIAL_CHARS] constant.
	 */
	MISSING_SPECIAL
}