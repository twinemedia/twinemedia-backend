package net.termer.twinemedia.util

import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.enumeration.PasswordValidationErrorType

/**
 * Validates a password using the validation settings defined in the provided config
 * @param password The password to validate
 * @param requireLength The required minimum length
 * @param requireUppercase Whether to require the password to contain an uppercase letter
 * @param requireNumber Whether to require the password to contain a numeric character (0-9)
 * @param requireSpecial Whether to require the password to contain a special character (special characters are defined in the [SPECIAL_CHARS] constant)
 * @return The validation error that was found in the password, or null if it is valid
 * @since 2.0.0
 */
fun validatePassword(
	password: String,
	requireLength: Int,
	requireUppercase: Boolean,
	requireNumber: Boolean,
	requireSpecial: Boolean
): PasswordValidationErrorType? {
	// Check length
	if(password.length < requireLength)
		return PasswordValidationErrorType.TOO_SHORT

	// Evaluate which requirements the string meets
	val chars = password.toCharArray()
	var hasUppercase = false
	var hasNumber = false
	var hasSpecial = false
	for(char in chars) {
		// Check if char is denied
		if(PASSWORD_INVALID_CHARS.contains(char))
			return PasswordValidationErrorType.INVALID_CHARS

		// Check for requirements
		when {
			char.isUpperCase() -> hasUppercase = true
			NUMBERS_CHARS.contains(char) -> hasNumber = true
			SPECIAL_CHARS.contains(char) -> hasSpecial = true
		}
	}

	// Check requirements
	if(requireUppercase && !hasUppercase)
		return PasswordValidationErrorType.MISSING_UPPERCASE
	if(requireNumber && !hasNumber)
		return PasswordValidationErrorType.MISSING_NUMBER
	if(requireSpecial && !hasSpecial)
		return PasswordValidationErrorType.MISSING_SPECIAL

	// All checks passed; no error to return
	return null
}

/**
 * Validates a password using the validation settings defined in the provided config
 * @param password The password to validate
 * @param config The config to get validation settings from
 * @return The validation error that was found in the password, or null if it is valid
 * @since 2.0.0
 */
fun validatePassword(password: String, config: AppConfig) = validatePassword(
	password,
	config.passwordRequireLength,
	config.passwordRequireUppercase,
	config.passwordRequireNumber,
	config.passwordRequireSpecial
)