package net.termer.twinemedia.model.pagination

import io.vertx.core.http.HttpServerRequest
import net.termer.twinemedia.dataobject.FileDto
import net.termer.twinemedia.model.FilesModel.*
import net.termer.twinemedia.util.*
import org.jooq.impl.DSL.*
import java.time.OffsetDateTime

/**
 * Abstract class for file pagination implementations
 * @since 2.0.0
 */
interface FilePagination<TColType>: RowPagination<FileDto, SortOrder, TColType> {
	companion object {
		@Suppress("UNCHECKED_CAST")
		private fun CommonPagination.TokenData<SortOrder, *>.toPagination(): FilePagination<*> {
			return when(sortEnum) {
				SortOrder.CREATED_TS ->
					CreatedTsPagination(this as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.MODIFIED_TS ->
					ModifiedTsPagination(this as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.TITLE_ALPHABETICALLY ->
					TitlePagination(this as CommonPagination.TokenData<SortOrder, String>)
				SortOrder.NAME_ALPHABETICALLY ->
					NamePagination(this as CommonPagination.TokenData<SortOrder, String>)
				SortOrder.FILE_SIZE ->
					FileSizePagination(this as CommonPagination.TokenData<SortOrder, Long>)
				SortOrder.TAG_COUNT ->
					TagCountPagination(this as CommonPagination.TokenData<SortOrder, Int>)
				SortOrder.CHILD_COUNT ->
					ChildCountPagination(this as CommonPagination.TokenData<SortOrder, Int>)
			}
		}

		/**
		 * Decodes a file pagination token into an [FilePagination] object
		 * @param token The token to decode
		 * @return The [FilePagination] object
		 * @throws PaginationTokenDecodeException If the token is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun decodeToken(token: String): FilePagination<*> {
			val bytes = Crypto.INSTANCE.aesDecrypt(token)

			// Extract sort enum
			val sortEnumVals = SortOrder.values()
			val sort = CommonPagination.decodeSortEnum(bytes, sortEnumVals)

			return when(sort) {
				SortOrder.CREATED_TS ->
					CommonPagination.Timestamp.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
				SortOrder.MODIFIED_TS ->
					CommonPagination.Timestamp.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
				SortOrder.TITLE_ALPHABETICALLY ->
					CommonPagination.Text.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
				SortOrder.NAME_ALPHABETICALLY ->
					CommonPagination.Text.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
				SortOrder.FILE_SIZE ->
					CommonPagination.Integer.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
				SortOrder.TAG_COUNT ->
					CommonPagination.Integer.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
				SortOrder.CHILD_COUNT ->
					CommonPagination.Integer.decodeTokenBytes(bytes, sortEnumVals, sort).toPagination()
			}
		}

		/**
		 * Resolves an [FilePagination] object based on request parameters
		 * @return The [FilePagination] object
		 * @throws PaginationTokenDecodeException If the token provided (if any) is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun HttpServerRequest.resolvePagination(): FilePagination<*> {
			val params = params()

			// Check for pagination token
			val pageToken = params["page"]
			if(pageToken == null) {
				// Extract ordering from params
				val order = if(params.contains("order"))
					SortOrder.values().getOr(
						params["order"].toIntOr(0),
						SortOrder.CREATED_TS
					)
				else
					SortOrder.CREATED_TS
				val orderDesc = params["orderDesc"] == "true"

				// Create token data without a cursor
				return CommonPagination.TokenData(
					sortEnum = order,
					isSortedByDesc = orderDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				).toPagination()
			} else {
				return decodeToken(pageToken)
			}
		}
	}

	/**
	 * File creation timestamp pagination
	 * @since 2.0.0
	 */
	class CreatedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): FilePagination<OffsetDateTime>, CommonPagination.Timestamp<FileDto, SortOrder>(
		timestampField = field("files.file_created_ts"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		CommonPagination::rowCreatedTsAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [CreatedTsPagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [CreatedTsPagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = CreatedTsPagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.CREATED_TS,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: OffsetDateTime) = CreatedTsPagination(
				CommonPagination.TokenData(
					SortOrder.CREATED_TS,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: OffsetDateTime?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}

	/**
	 * File last modified timestamp pagination
	 * @since 2.0.0
	 */
	class ModifiedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): FilePagination<OffsetDateTime>, CommonPagination.Timestamp<FileDto, SortOrder>(
		timestampField = field("files.file_modified_ts"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		rowColumnAccessor = CommonPagination::rowModifiedTsAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [ModifiedTsPagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [ModifiedTsPagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = ModifiedTsPagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.MODIFIED_TS,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: OffsetDateTime) = ModifiedTsPagination(
				CommonPagination.TokenData(
					SortOrder.MODIFIED_TS,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: OffsetDateTime?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}

	/**
	 * File title pagination
	 * @since 2.0.0
	 */
	class TitlePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): FilePagination<String>, CommonPagination.Text<FileDto, SortOrder>(
		textField = field("files.file_title"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		/**
		 * Creates a no-cursor [NamePagination] object
		 * @param sortDesc Whether results will be returned in descending order
		 * @return The [NamePagination] instance
		 * @since 2.0.0
		 */
		fun create(sortDesc: Boolean) = NamePagination(
			CommonPagination.TokenData(
				sortEnum = SortOrder.NAME_ALPHABETICALLY,
				isSortedByDesc = sortDesc,
				isPreviousCursor = false,
				columnValue = null,
				internalId = null
			)
		)

		companion object {
			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: String) = NamePagination(
				CommonPagination.TokenData(
					SortOrder.NAME_ALPHABETICALLY,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: FileDto) = row.name
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: String?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}

	/**
	 * File name pagination
	 * @since 2.0.0
	 */
	class NamePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): FilePagination<String>, CommonPagination.Text<FileDto, SortOrder>(
		textField = field("files.file_name"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		/**
		 * Creates a no-cursor [NamePagination] object
		 * @param sortDesc Whether results will be returned in descending order
		 * @return The [NamePagination] instance
		 * @since 2.0.0
		 */
		fun create(sortDesc: Boolean) = NamePagination(
			CommonPagination.TokenData(
				sortEnum = SortOrder.NAME_ALPHABETICALLY,
				isSortedByDesc = sortDesc,
				isPreviousCursor = false,
				columnValue = null,
				internalId = null
			)
		)

		companion object {
			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: String) = NamePagination(
				CommonPagination.TokenData(
					SortOrder.NAME_ALPHABETICALLY,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: FileDto) = row.name
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: String?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}

	/**
	 * File size pagination
	 * @since 2.0.0
	 */
	class FileSizePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, Long>
	): FilePagination<Long>, CommonPagination.LongInteger<FileDto, SortOrder>(
		longField = field("files.file_size"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [FileSizePagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [FileSizePagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = NamePagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.FILE_SIZE,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: Long) = FileSizePagination(
				CommonPagination.TokenData(
					SortOrder.FILE_SIZE,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: FileDto) = row.size
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: Long?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}

	/**
	 * File tag count pagination
	 * @since 2.0.0
	 */
	class TagCountPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, Int>
	): FilePagination<Int>, CommonPagination.Integer<FileDto, SortOrder>(
		intField = field("files.file_tag_count"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [TagCountPagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [TagCountPagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = NamePagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.TAG_COUNT,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: Int) = TagCountPagination(
				CommonPagination.TokenData(
					SortOrder.TAG_COUNT,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: FileDto) = row.tagCount
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: Int?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}

	/**
	 * File child count pagination
	 * @since 2.0.0
	 */
	class ChildCountPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, Int>
	): FilePagination<Int>, CommonPagination.Integer<FileDto, SortOrder>(
		intField = field("files.file_child_count"),
		internalIdField = field("files.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [ChildCountPagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [ChildCountPagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = NamePagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.CHILD_COUNT,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: Int) = ChildCountPagination(
				CommonPagination.TokenData(
					SortOrder.CHILD_COUNT,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: FileDto) = row.childCount
		}

		override val sortType: SortOrder
			get() = tokenData.sortEnum

		override val isSortedByDesc: Boolean
			get() = tokenData.isSortedByDesc

		override val isPreviousCursor: Boolean
			get() = tokenData.isPreviousCursor

		override val columnValue: Int?
			get() = tokenData.columnValue

		override val internalId: Int?
			get() = tokenData.internalId
	}
}