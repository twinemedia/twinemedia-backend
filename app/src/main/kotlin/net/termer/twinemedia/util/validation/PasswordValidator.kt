package net.termer.twinemedia.util.validation

import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.enumeration.PasswordValidationErrorType.*
import net.termer.twinemedia.util.validatePassword
import net.termer.vertx.kotlin.validation.ParamValidator

/**
 * Validator for passwords
 * @param config The [AppConfig] from which to derive password requirements
 * @author termer
 * @since 1.4.0
 */
open class PasswordValidator(private val config: AppConfig): ParamValidator {
	override fun validate(param: ParamValidator.Param): ParamValidator.ValidatorResponse {
		val password = param.value

		// Return appropriate validation response
		return when(val valRes = validatePassword(password, config)) {
			TOO_SHORT -> ParamValidator.ValidatorResponse(valRes.name, "The password is too short (minimum length is ${config.passwordRequireLength})")
			INVALID_CHARS -> ParamValidator.ValidatorResponse(valRes.name, "The password contains invalid characters (such as newlines, tabs, or other control characters)")
			MISSING_UPPERCASE -> ParamValidator.ValidatorResponse(valRes.name, "The password must have an uppercase character")
			MISSING_NUMBER -> ParamValidator.ValidatorResponse(valRes.name, "The password must have a number")
			MISSING_SPECIAL -> ParamValidator.ValidatorResponse(valRes.name, "The password must have a special character")
			null -> ParamValidator.ValidatorResponse(password)
		}
	}
}