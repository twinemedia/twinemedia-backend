package net.termer.twinemedia.util.validation

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonArray
import net.termer.vertx.kotlin.validation.ParamValidator

/**
 * Validator for JSON arrays of permissions parameters
 * @author termer
 * @since 1.4.0
 */
open class PermissionsValidator: ParamValidator {
	private var minLen: Int? = null
	private var maxLen: Int? = null

	override fun validate(param: ParamValidator.Param): ParamValidator.ValidatorResponse {
		try {
			val arr = JsonArray(param.value)
			val list = arr.list

			// Check length
			if(minLen != null && list.size < minLen!!)
				return ParamValidator.ValidatorResponse("INVALID_LENGTH", "The JSON array is too short (minimum length is $minLen)")
			if(maxLen != null && list.size < maxLen!!)
				return ParamValidator.ValidatorResponse("INVALID_LENGTH", "The JSON array is too long (maximum length is $maxLen)")

			// Validate types and permissions
			for((i, item) in list.withIndex()) {
				// Type validation
				if(item == null)
					return ParamValidator.ValidatorResponse("INVALID_ITEM", "The JSON array cannot contain any nulls, and the item at index $i is null")
				if(item !is String)
					return ParamValidator.ValidatorResponse("INVALID_ITEM", "The item at index $i of the JSON array is not a string")

				// Trim the permission
				val perm = item.trim()

				// Permission validation
				when {
					perm.isBlank() -> return ParamValidator.ValidatorResponse("INVALID_PERMISSION", "Permissions cannot be blank, and the item at index $i is blank")
					perm.startsWith('.') -> return ParamValidator.ValidatorResponse("INVALID_PERMISSION", "Permissions cannot start with dots, and the item at index $i starts with a dot")
					perm.contains(' ') -> return ParamValidator.ValidatorResponse("INVALID_PERMISSION", "Permissions cannot contain spaces, and the item at index $i contains a space")
					perm.contains('"') -> return ParamValidator.ValidatorResponse("INVALID_PERMISSION", "Permissions cannot contain quotes, and the item at index $i contains a quote")
				}
			}
			
			return ParamValidator.ValidatorResponse(arr)
		} catch(e: DecodeException) {
			return ParamValidator.ValidatorResponse("INVALID_JSON", "The provided value does not represent a JSON array")
		}
	}

	/**
	 * Requires a minimum length
	 * @param length The required minimum length
	 * @return This, to be used fluently
	 * @since 1.4.0
	 */
	fun minLength(length: Int): PermissionsValidator {
		minLen = length
		return this
	}
	/**
	 * Requires a maximum length
	 * @param length The required maximum length
	 * @return This, to be used fluently
	 * @since 1.4.0
	 */
	fun maxLength(length: Int): PermissionsValidator {
		maxLen = length
		return this
	}
}