@file:Suppress("DEPRECATION")

package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.dataobject.AccountRow
import net.termer.twinemedia.util.Option
import org.jooq.ConditionProvider
import org.jooq.UpdateQuery
import org.jooq.impl.DSL.falseCondition
import org.jooq.impl.DSL.field

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
	 * Values should generally be instances of [Option], do not use null to signify that a value should not be used for filtering.
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
	 * Types of context filters that can be applied to queries
	 * @since 2.0.0
	 */
	enum class ContextFilterType {
		/**
		 * A view filter.
		 * Applies to single-row/DTO queries.
		 */
		VIEW,

		/**
		 * A list filter.
		 * Applies to multiple-row/DTO queries.
		 */
		LIST,

		/**
		 * An update filter.
		 * Applies to update queries.
		 */
		UPDATE,

		/**
		 * A delete filter.
		 * Applies to delete queries.
		 */
		DELETE;

		/**
		 * Returns the corresponding permission verb for this context filter type
		 * @return The permission verb
		 * @since 2.0.0
		 */
		fun toPermissionVerb() = when(this) {
			VIEW -> "view"
			LIST -> "list"
			UPDATE -> "edit"
			DELETE -> "delete"
		}
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

	/**
	 * Applies a generic permission and row creator-based context filter on a query.
	 * Evaluates whether a user can access rows based on whether the context account is the creator of the row, or the context account has the "all" variant of the specified permission.
	 * Permissions use the standard <data>.<verb>(.all) format.
	 * The verb is selected based on [type] according to standard permission verbs ("view", "list", "edit" and "delete").
	 * If [context] is null, a false condition is applied to the query.
	 * If [ignoreContext] is true, no conditions are added, and the query is left unmodified.
	 * @param query The query to apply the filter to
	 * @param type The context filter type
	 * @param permissionPrefix The prefix of the permission to check, usually the resource that is being filtered (e.g. "files")
	 * @param creatorField The row field that contains the row creator
	 * @param ignoreAllPermission Whether to ignore that the context account has the "all" variant of a permission, and only show the account's files (typically you would use an account's exclude preference to fill this value)
	 * @since 2.0.0
	 */
	protected fun applyGenericPermissionCreatorContextFilter(
		query: ConditionProvider,
		type: ContextFilterType,
		permissionPrefix: String,
		creatorField: String,
		ignoreAllPermission: Boolean?
	) {
		if(ignoreContext)
			return

		if(context == null) {
			// If there is no context, do not show any rows
			query.addConditions(falseCondition())
			return
		}

		val acc = context!!.account

		// Add filter condition
		if(ignoreAllPermission == true || !acc.hasPermission("$permissionPrefix.${type.toPermissionVerb()}.all"))
			query.addConditions(field(creatorField).eq(acc.internalId))
	}
}