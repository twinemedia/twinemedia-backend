package net.termer.twinemedia.util.validation

import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.util.NUMBERS_CHARS
import net.termer.twinemedia.util.PASSWORD_INVALID_CHARS
import net.termer.twinemedia.util.SPECIAL_CHARS
import net.termer.vertx.kotlin.validation.ParamValidator

/**
 * Validator for passwords
 * @param config The [AppConfig] from which to derive password requirements
 * @author termer
 * @since 1.4.0
 */
open class PasswordValidator(private val config: AppConfig): ParamValidator {
	override fun validate(param: ParamValidator.Param): ParamValidator.ValidatorResponse {
		val str = param.value

		// Check length
		if(str.length < config.passwordRequireLength)
			return ParamValidator.ValidatorResponse("TOO_SHORT", "The password is too short (minimum length is ${config.passwordRequireLength})")

		// Check for requirements
		val chars = str.toCharArray()
		var hasUppercase = false
		var hasNumber = false
		var hasSpecial = false
		for(char in chars) {
			// Check if char is denied
			if(PASSWORD_INVALID_CHARS.contains(char))
				return ParamValidator.ValidatorResponse("INVALID_CHARS", "The password contains invalid characters (such as newlines, tabs, or other control characters)")

			// Check for requirements
			when {
				char.isUpperCase() -> hasUppercase = true
				NUMBERS_CHARS.contains(char) -> hasNumber = true
				SPECIAL_CHARS.contains(char) -> hasSpecial = true
			}
		}

		// Check requirements against config
		if(!hasUppercase && config.passwordRequireUppercase)
			return ParamValidator.ValidatorResponse("MISSING_UPPERCASE", "The password must have an uppercase character")
		if(!hasNumber && config.passwordRequireNumber)
			return ParamValidator.ValidatorResponse("MISSING_NUMBER", "The password must have a number")
		if(!hasSpecial && config.passwordRequireSpecial)
			return ParamValidator.ValidatorResponse("MISSING_SPECIAL", "The password must have a special character")

		// If it got to this point, all is well, return the original password
		return ParamValidator.ValidatorResponse(str)
	}
}