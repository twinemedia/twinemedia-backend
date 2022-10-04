package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.dataobject.AccountRow
import org.jooq.SelectQuery

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
	 * Interface for model fetch options.
	 * Includes filters and related values.
	 * Options should have default values where applicable.
	 * Using [applyTo] should never modify the instance, and it should be reusable across queries.
	 * @since 2.0.0
	 */
	interface FetchOptions {
		/**
		 * Applies the filters on the provided select query
		 * @param query The query
		 * @since 2.0.0
		 */
		fun applyTo(query: SelectQuery<*>)

		/**
		 * Changes the instance's filters based on the provided [HttpServerRequest]
		 * @param req The request
		 * @since 2.0.0
		 */
		fun useRequest(req: HttpServerRequest)
	}
}