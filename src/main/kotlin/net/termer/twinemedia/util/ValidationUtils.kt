package net.termer.twinemedia.util

import net.termer.twinemedia.util.validation.Presets
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.IntValidator

/**
 * Adds optional "offset" and "limit" Int params
 * @return This, to be used fluently
 * @since 1.5.0
 */
fun RequestValidator.offsetLimit(): RequestValidator {
	optionalParam("offset", Presets.resultOffsetValidator(), 0)
	optionalParam("limit", Presets.resultLimitValidator(), 100)

	return this
}

/**
 * Adds an optional "order" Int param with the specified max value
 * @return This, to be used fluently
 * @since 1.5.0
 */
fun RequestValidator.order(max: Int): RequestValidator {
	optionalParam("order",
			IntValidator()
					.coerceMin(0)
					.coerceMax(max),
			0)

	return this
}

/**
 * Alias to offsetLimit() and order(max)
 * @return This, to be used fluently
 * @since 1.5.0
 */
fun RequestValidator.offsetLimitOrder(maxOrder: Int): RequestValidator {
	offsetLimit()
	order(maxOrder)

	return this
}