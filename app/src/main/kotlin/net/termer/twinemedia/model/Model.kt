@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.util.Option
import org.jooq.ConditionProvider
import org.jooq.UpdateQuery

/**
 * Abstract class to be implemented by database models.
 * Models can filter results based on context or lack of context, or ignore by setting [ignoreContext].
 * @since 2.0.0
 */
abstract class Model(
	/**
	 * The context associated with this model instance, or null for none
	 * @since 2.0.0
	 */
	var context: Context?,
	/**
	 * Whether this model's context (or lack thereof) should be ignored for the purpose of query filtering
	 * @since 2.0.0
	 */
	var ignoreContext: Boolean
) {
	/**
	 * Model context, for the purpose of query filtering
	 * @since 2.0.0
	 */
	data class Context(
		/**
		 * The observer's account
		 * @since 2.0.0
		 */
		val account: AccountRow
	)

	/**
	 * Interface for model select, update and delete filters.
	 * Includes filters and related values.
	 * Filters should have default values where applicable.
	 * Using [applyTo] should never modify the instance, and it should be reusable across queries.
	 * @since 2.0.0
	 */
	interface Filters {
		/**
		 * Applies the filters on the provided query.
		 * The query must implement [ConditionProvider].
		 * @param query The query to apply filters on
		 * @since 2.0.0
		 */
		fun applyTo(query: ConditionProvider)

		/**
		 * Changes the instance's filters based on the provided [HttpServerRequest].
		 * Only API-safe filters should be changed by this method.
		 * @param req The request
		 * @since 2.0.0
		 */
		fun setWithRequest(req: HttpServerRequest)
	}

	/**
	 * Interface for update values.
	 * Includes values that are to be updated on model rows.
	 * Values should be instances of [Option], do not use null to signify that a value should not be updated.
	 * Using [applyTo] should never modify the instance, and it should be reusable across queries.
	 * @since 2.0.0
	 */
	interface UpdateValues {
		/**
		 * Applies the update values to the provided update query
		 * @param query The update query to apply values on
		 * @since 2.0.0
		 */
		fun applyTo(query: UpdateQuery<*>)
	}
}