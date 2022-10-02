package net.termer.twinemedia.dataobject

/**
 * A row order direction.
 * Specifies whether rows should be ordered in ascending or descending order.
 * Use the [ASC] and [DESC] values instead of instantiating the object.
 * @since 2.0.0
 */
@JvmInline
value class RowOrderDirection(val isDesc: Boolean) {
	companion object {
		/**
		 * Ascending order
		 * @since 2.0.0
		 */
		val ASC = RowOrderDirection(false)

		/**
		 * Descending order
		 * @since 2.0.0
		 */
		val DESC = RowOrderDirection(true)
	}
}