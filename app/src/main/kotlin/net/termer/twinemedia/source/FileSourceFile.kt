package net.termer.twinemedia.source

import java.time.OffsetDateTime

/**
 * Class that contains basic info about a file in a file source
 * @author termer
 * @since 1.5.0
 */
class FileSourceFile(
		/**
		 * The file's key
		 * @since 1.5.0
		 */
		val key: String,

		/**
		 * The file's URL, or null if not available
		 * @since 1.5.0
		 */
		val url: String? = null,

		/**
		 * The file's MIME type, or null if not available
		 * @since 1.5.0
		 */
		val mime: String? = null,

		/**
		 * The file's size, or null if not available
		 * @since 1.5.0
		 */
		val size: Long? = null,

		/**
		 * The file's hash, or null if not available
		 * @since 1.5.0
		 */
		val hash: String? = null,

		/**
		 * The file's creation timestamp, or null if not available
		 * @since 2.0.0
		 */
		val createdTs: OffsetDateTime? = null,

		/**
		 * The file's last modified timestamp, or null if not available
		 * @since 2.0.0
		 */
		val modifiedTs: OffsetDateTime? = null
)