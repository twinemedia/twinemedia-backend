package net.termer.twinemedia.util.validation

import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Utility class for creating validator presets
 * @author termer
 * @since 1.4.0
 */
class Presets {
	companion object {
		/**
		 * Creates a new account name StringValidator
		 * @return A new account name StringValidator
		 * @since 1.4.0
		 */
		fun accountNameValidator() = StringValidator()
				.trim()
				.notBlank()
				.noNewlinesOrControlChars()
				.minLength(1)
				.maxLength(64)

		/**
		 * Creates a new result offset IntValidator
		 * @return A new result offset IntValidator
		 * @since 1.4.0
		 */
		fun resultOffsetValidator() = IntValidator()
				.coerceMin(0)

		/**
		 * Creates a new result limit IntValidator
		 * @return A new result limit IntValidator
		 * @since 1.4.0
		 */
		fun resultLimitValidator() = IntValidator()
				.coerceMin(0)
				.coerceMax(100)

		/**
		 * Creates a new list type IntValidator, optionally allowing for -1 to specify no type
		 * @return A new list type IntValidator
		 * @since 1.4.0
		 */
		fun listTypeValidator(allowNegativeOne: Boolean) = IntValidator()
				.min(if(allowNegativeOne) -1 else 0)
				.max(ListType.values().size)

		/**
		 * Creates a new list visibility IntValidator, optionally allowing for -1 to specify no visibility
		 * @return A new list visibility IntValidator
		 * @since 1.4.0
		 */
		fun listVisibilityValidator(allowNegativeOne: Boolean) = IntValidator()
				.min(if(allowNegativeOne) -1 else 0)
				.max(ListVisibility.values().size)

		/**
		 * Creates a new MIME type StringValidator, optionally allowing SQL wildcards in it
		 * @param allowWildcard Whether to allow SQL wildcards
		 * @return A new MIME type StringValidator
		 * @since 1.4.0
		 */
		fun mimeValidator(allowWildcard: Boolean) = StringValidator()
				.toLowerCase()
				.regex(if(allowWildcard) Regex("^[%\\-\\w.]+(/[\\\\%\\-\\w.]+)?$") else Regex("^[-\\w.]+(/[-\\w.]+)?$"))
	}
}