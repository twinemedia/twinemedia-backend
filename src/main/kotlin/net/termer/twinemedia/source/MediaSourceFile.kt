package net.termer.twinemedia.source

import java.time.OffsetDateTime

/**
 * Class that contains basic info about a file in a media source
 * @author termer
 * @since 1.5.0
 */
class MediaSourceFile(
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
		 * The time the file was created, or null if not available
		 * @since 1.5.0
		 */
		val createdOn: OffsetDateTime? = null,
		/**
		 * The time the file was last modified, or null if not available
		 * @since 1.5.0
		 */
		val modifiedOn: OffsetDateTime? = null,
		/**
		 * The file's hash, or null if not available
		 * @since 1.5.0
		 */
		val hash: String? = null
)