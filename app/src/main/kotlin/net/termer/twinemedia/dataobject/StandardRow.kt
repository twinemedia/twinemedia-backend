package net.termer.twinemedia.dataobject

import java.time.OffsetDateTime

/**
 * A row object or DTO with standard row values
 * @since 2.0.0
 */
interface StandardRow {
	/**
	 * The row's internal sequential ID.
	 * This should not be exposed in JSON.
	 * @since 2.0.0
	 */
	val internalId: Int

	/**
	 * The row's alphanumeric ID
	 * @since 2.0.0
	 */
	val id: String

	/**
	 * The row's creation timestamp
	 * @since 2.0.0
	 */
	val createdTs: OffsetDateTime

	/**
	 * The row's last modified timestamp
	 * @since 2.0.0
	 */
	val modifiedTs: OffsetDateTime
}
