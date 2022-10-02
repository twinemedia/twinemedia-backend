package net.termer.twinemedia.dataobject

/**
 * A row's ID pair, consisting of its internal sequential ID, and its alphanumeric ID.
 * @since 2.0.0
 */
data class RowIdPair(
	/**
	 * The row's internal sequential ID
	 * @since 2.0.0
	 */
	val internalId: Int,

	/**
	 * The row's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String
)
