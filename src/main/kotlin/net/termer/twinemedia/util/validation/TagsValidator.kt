package net.termer.twinemedia.util.validation

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonArray
import net.termer.vertx.kotlin.validation.ParamValidator

/**
 * Validator for JSON arrays of tags parameters
 * @author termer
 * @since 1.4.0
 */
open class TagsValidator: ParamValidator {
	private var minLen: Int? = null
	private var maxLen: Int? = null

	override fun validate(param: ParamValidator.Param): ParamValidator.ValidatorResponse {
		try {
			val arr = JsonArray(param.value)
			val list = arr.list
			val tags = JsonArray()

			// Check length
			if(minLen != null && list.size < minLen!!)
				return ParamValidator.ValidatorResponse("INVALID_LENGTH", "The JSON array is too short (minimum length is $minLen)")
			if(maxLen != null && list.size < maxLen!!)
				return ParamValidator.ValidatorResponse("INVALID_LENGTH", "The JSON array is too long (maximum length is $maxLen)")

			// Validate types and tags
			for((i, item) in list.withIndex()) {
				// Type validation
				if(item == null)
					return ParamValidator.ValidatorResponse("INVALID_ITEM", "The JSON array cannot contain any nulls, and the item at index $i is null")
				if(item !is String)
					return ParamValidator.ValidatorResponse("INVALID_ITEM", "The item at index $i of the JSON array is not a string")

				// Process the tag
				val tag = item.trim().lowercase()

				// Tag validation
				when {
					tag.startsWith('-') -> return ParamValidator.ValidatorResponse("INVALID_TAG", "Tags cannot start with dashes, and the item at index $i starts with a dash")
					tag.contains(' ') -> return ParamValidator.ValidatorResponse("INVALID_TAG", "Tags cannot contain spaces, and the item at index $i contains a space")
					tag.contains('"') -> return ParamValidator.ValidatorResponse("INVALID_TAG", "Tags cannot contain quotes, and the item at index $i contains a quote")
				}

				// Add tag if it's not already there (and not blank)
				if(tag.isNotBlank() && !tags.contains(tag))
					tags.add(tag)
			}

			return ParamValidator.ValidatorResponse(tags)
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
	fun minLength(length: Int): TagsValidator {
		minLen = length
		return this
	}
	/**
	 * Requires a maximum length
	 * @param length The required maximum length
	 * @return This, to be used fluently
	 * @since 1.4.0
	 */
	fun maxLength(length: Int): TagsValidator {
		maxLen = length
		return this
	}
}