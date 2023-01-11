package net.termer.twinemedia.model

import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.RoutingContext
import net.termer.twinemedia.dataobject.StandardRow
import net.termer.twinemedia.util.Option
import net.termer.twinemedia.util.Some
import net.termer.twinemedia.util.account.AccountContext
import net.termer.twinemedia.util.dateStringToOffsetDateTimeOrNone
import net.termer.twinemedia.util.validation.accountContext
import org.jooq.Condition
import org.jooq.UpdateQuery
import org.jooq.impl.DSL.falseCondition
import org.jooq.impl.DSL.field
import java.time.OffsetDateTime

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
		 * The observer's account context
		 * @since 2.0.0
		 */
		val account: AccountContext
	) {
		companion object {
			/**
			 * Creates a new [Model.Context] instance using context from the provided [RoutingContext], or null if the request is not authenticated
			 * @param ctx The [RoutingContext]
			 * @return The new [Context] instance, or null if the request is not authenticated
			 * @since 2.0.0
			 */
			fun fromRequest(ctx: RoutingContext): Context? {
				val accCtx = ctx.accountContext()
				return if (accCtx == null) null else Context(accCtx)
			}
		}
	}

	/**
	 * Abstract class for model select, update and delete filters.
	 * Includes filters and related values.
	 * Filters should have default values where applicable.
	 * Values should generally be instances of [Option], do not use null to signify that a value should not be used for filtering.
	 * Using [genConditions] should never modify the instance, and it should be reusable across queries.
	 * @since 2.0.0
	 */
	abstract class Filters(
		/**
		 * The table these filters apply to
		 */
		protected val table: String,

		/**
		 * The column prefix for this table (e.g. "file" if columns have names like "file_id")
		 */
		protected val colPrefix: String
	) {
		/**
		 * Generates jOOQ conditions for this model's filters
		 * @return The conditions
		 * @since 2.0.0
		 */
		abstract fun genConditions(): MutableList<Condition>

		/**
		 * Changes the instance's filters based on the provided [HttpServerRequest].
		 * Only API-safe filters should be changed by this method.
		 * @param req The request
		 * @since 2.0.0
		 */
		abstract fun setWithRequest(req: HttpServerRequest)
	}

	/**
	 * Abstract class for model select, update and delete filters on tables with rows that implement [StandardRow].
	 * Provides filters for standard rows.
	 * See documentation on [Filters] for more information.
	 * @since 2.0.0
	 */
	abstract class StandardFilters(table: String, colPrefix: String): Filters(table, colPrefix) {
		/**
		 * Matches rows where the sequential internal ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		abstract var whereInternalIdIs: Option<Int>

		/**
		 * Matches rows where the alphanumeric ID is this.
		 * API-unsafe.
		 * @since 2.0.0
		 */
		abstract var whereIdIs: Option<String>

		/**
		 * Matches rows created before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		abstract var whereCreatedBefore: Option<OffsetDateTime>

		/**
		 * Matches rows created after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		abstract var whereCreatedAfter: Option<OffsetDateTime>

		/**
		 * Matches rows modified before this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		abstract var whereModifiedBefore: Option<OffsetDateTime>

		/**
		 * Matches rows modified after this time.
		 * API-safe.
		 * @since 2.0.0
		 */
		abstract var whereModifiedAfter: Option<OffsetDateTime>

		/**
		 * Generates standard filter conditions for a query.
		 * This should be used in the beginning of the [genConditions] implementation.
		 */
		protected fun genStandardConditions(): MutableList<Condition> {
			val prefix = "$table.$colPrefix"

			val res = ArrayList<Condition>()

			if(whereInternalIdIs is Some)
				res.add(field("$table.id").eq((whereInternalIdIs as Some).value))
			if(whereIdIs is Some)
				res.add(field("${prefix}_id").eq((whereIdIs as Some).value))
			if(whereCreatedBefore is Some)
				res.add(field("${prefix}_created_ts").lt((whereCreatedBefore as Some).value))
			if(whereCreatedAfter is Some)
				res.add(field("${prefix}_created_ts").gt((whereCreatedAfter as Some).value))
			if(whereModifiedBefore is Some)
				res.add(field("${prefix}_modified_ts").lt((whereModifiedBefore as Some).value))
			if(whereModifiedAfter is Some)
				res.add(field("${prefix}_modified_ts").gt((whereModifiedAfter as Some).value))

			return res
		}

		/**
		 * Changes the instance's filters based on the provided [HttpServerRequest] for API-safe standard filters.
		 * This should be called in the beginning of the [setWithRequest] implementation.
		 */
		protected fun setStandardFiltersWithRequest(req: HttpServerRequest) {
			val params = req.params()

			if(params.contains("whereCreatedAfter"))
				whereCreatedAfter = dateStringToOffsetDateTimeOrNone(params["whereCreatedAfter"])
			if(params.contains("whereModifiedBefore"))
				whereModifiedBefore = dateStringToOffsetDateTimeOrNone(params["whereModifiedBefore"])
			if(params.contains("whereModifiedAfter"))
				whereModifiedAfter = dateStringToOffsetDateTimeOrNone(params["whereModifiedAfter"])
		}
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
	 * Generates generic permission and row owner-based context filter conditions.
	 * Evaluates whether a user can access rows based on whether the context account is the owner of the row, or the context account has the "all" variant of the specified permission.
	 * Permissions use the standard <data>.<verb>(.all) format.
	 * The verb is selected based on [type] according to standard permission verbs ("view", "list", "edit" and "delete").
	 * If [context] is null, a false condition is applied to the query.
	 * If [ignoreContext] is true, no conditions are added, and the query is left unmodified.
	 * @param type The context filter type
	 * @param permissionPrefix The prefix of the permission to check, usually the resource that is being filtered (e.g. "files")
	 * @param ownerField The row field that contains the row owner
	 * @param ignoreAllPermission Whether to ignore that the context account has the "all" variant of a permission, and only show the account's files (typically you would use an account's exclude preference to fill this value)
	 * @since 2.0.0
	 */
	protected fun genGenericPermissionOwnerContextConditions(
		type: ContextFilterType,
		permissionPrefix: String,
		ownerField: String,
		ignoreAllPermission: Boolean?
	): MutableList<Condition> {
		if(ignoreContext)
			return ArrayList(0)

		if(context == null) {
			// If there is no context, do not show any rows
			return arrayListOf(falseCondition())
		}

		val acc = context!!.account

		// Add filter condition
		if(ignoreAllPermission == true || !acc.hasPermission("$permissionPrefix.${type.toPermissionVerb()}.all"))
			return arrayListOf(field(ownerField).eq(acc.selfAccount.internalId))

		// No conditions to return
		return ArrayList(0)
	}
}
