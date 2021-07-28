package net.termer.twinemedia.util.validation

import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twinemedia.Module.Companion.config
import net.termer.vertx.kotlin.validation.ParamValidator

/**
 * Validator for passwords
 * @author termer
 * @since 1.4.0
 */
@DelicateCoroutinesApi
open class PasswordValidator: ParamValidator {
	private val numChars = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
	private val specialChars = arrayOf('`', '~', '!', '@', '#', '$', '$', '%', '^', '&', '*', '(', ')', '_', '-', '=', '+', '\\', '|', ';', ':', '\'', '"', ',', '<', '.', '>', '/', '?', '[','{', ']', '}')
	private val deniedChars = arrayOf('\n', '\t', '\r', ' ')

	override fun validate(param: ParamValidator.Param): ParamValidator.ValidatorResponse {
		val str = param.value

		// Check length
		if(str.length < config.password_require_min)
			return ParamValidator.ValidatorResponse("TOO_SHORT", "The password is too short (minimum length is ${config.password_require_min})")
		else if(str.length > 64)
			return ParamValidator.ValidatorResponse("TOO_LONG", "The password is too long (maximum length is 64)")

		// Check for requirements
		val chars = str.toCharArray()
		var hasUppercase = false
		var hasNumber = false
		var hasSpecial = false
		for(char in chars) {
			// Check if char is denied
			if(deniedChars.contains(char))
				return ParamValidator.ValidatorResponse("INVALID_CHARS", "The password contains invalid characters (such as spaces)")

			// Check for requirements
			when {
				char.isUpperCase() -> hasUppercase = true
				numChars.contains(char) -> hasNumber = true
				specialChars.contains(char) -> hasSpecial = true
			}
		}

		// Check requirements against config
		if(!hasUppercase && config.password_require_uppercase)
			return ParamValidator.ValidatorResponse("MISSING_UPPERCASE", "The password must have an uppercase character")
		if(!hasNumber && config.password_require_number)
			return ParamValidator.ValidatorResponse("MISSING_NUMBER", "The password must have a number")
		if(!hasSpecial && config.password_require_special)
			return ParamValidator.ValidatorResponse("MISSING_SPECIAL", "The password must have a special character")

		// If it got to this point, all is well, return the original password
		return ParamValidator.ValidatorResponse(str)
	}
}