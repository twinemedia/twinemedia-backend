package net.termer.twinemedia.model.pagination

import io.vertx.ext.web.validation.RequestParameters
import net.termer.twinemedia.dataobject.TagDto
import net.termer.twinemedia.model.TagsModel.*
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.util.*
import org.jooq.impl.DSL.*
import java.time.OffsetDateTime

/**
 * Abstract class for tag pagination implementations
 * @since 2.0.0
 */
interface TagPagination<TColType>: RowPagination<TagDto, SortOrder, TColType> {
	companion object {
		@Suppress("UNCHECKED_CAST")
		private fun toPagination(tokenData: CommonPagination.TokenData<SortOrder, *>): TagPagination<*> {
			return when(tokenData.sortEnum) {
				SortOrder.CREATED_TS ->
					CreatedTsPagination(tokenData as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.MODIFIED_TS ->
					ModifiedTsPagination(tokenData as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.NAME_ALPHABETICALLY ->
					NamePagination(tokenData as CommonPagination.TokenData<SortOrder, String>)
				SortOrder.FILE_COUNT ->
					FileCountPagination(tokenData as CommonPagination.TokenData<SortOrder, Int>)
			}
		}

		/**
		 * Decodes a tag pagination token into an [TagPagination] object
		 * @param token The token to decode
		 * @return The [TagPagination] object
		 * @throws PaginationTokenDecodeException If the token is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun decodeToken(token: String): TagPagination<*> {
			val bytes = CryptoService.INSTANCE.aesDecrypt(token)

			// Extract sort enum
			val sortEnumVals = SortOrder.values()
			val sort = CommonPagination.decodeSortEnum(bytes, sortEnumVals)

			return when(sort) {
				SortOrder.CREATED_TS ->
					toPagination(CommonPagination.Timestamp.decodeTokenBytes(bytes, sortEnumVals, sort))
				SortOrder.MODIFIED_TS ->
					toPagination(CommonPagination.Timestamp.decodeTokenBytes(bytes, sortEnumVals, sort))
				SortOrder.NAME_ALPHABETICALLY ->
					toPagination(CommonPagination.Text.decodeTokenBytes(bytes, sortEnumVals, sort))
				SortOrder.FILE_COUNT ->
					toPagination(CommonPagination.Integer.decodeTokenBytes(bytes, sortEnumVals, sort))
			}
		}

		/**
		 * Resolves an [TagPagination] object based on request parameters
		 * @param params The request parameters to resolve pagination from
		 * @return The [TagPagination] object
		 * @throws PaginationTokenDecodeException If the token provided (if any) is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun resolvePaginationFromJson(params: RequestParameters): TagPagination<*> {
			return CommonPagination.resolvePaginationFromParameters(params, SortOrder.CREATED_TS, ::toPagination, ::decodeToken)
		}
	}

	/**
	 * Tag creation timestamp pagination
	 * @since 2.0.0
	 */
	class CreatedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): TagPagination<OffsetDateTime>, CommonPagination.Timestamp<TagDto, SortOrder>(
		timestampField = field("tags.tag_created_ts"),
		internalIdField = field("tags.id"),
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
	 * Tag last modified timestamp pagination
	 * @since 2.0.0
	 */
	class ModifiedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): TagPagination<OffsetDateTime>, CommonPagination.Timestamp<TagDto, SortOrder>(
		timestampField = field("tags.tag_modified_ts"),
		internalIdField = field("tags.id"),
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
	 * Tag name pagination
	 * @since 2.0.0
	 */
	class NamePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): TagPagination<String>, CommonPagination.Text<TagDto, SortOrder>(
		textField = field("tags.tag_name"),
		internalIdField = field("tags.id"),
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

			fun rowAccessor(row: TagDto) = row.name
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
	 * Tag file count pagination
	 * @since 2.0.0
	 */
	class FileCountPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, Int>
	): TagPagination<Int>, CommonPagination.Integer<TagDto, SortOrder>(
		intField = field("tags.tag_file_count"),
		internalIdField = field("tags.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		/**
		 * Creates a no-cursor [FileCountPagination] object
		 * @param sortDesc Whether results will be returned in descending order
		 * @return The [FileCountPagination] instance
		 * @since 2.0.0
		 */
		fun create(sortDesc: Boolean) = FileCountPagination(
			CommonPagination.TokenData(
				sortEnum = SortOrder.FILE_COUNT,
				isSortedByDesc = sortDesc,
				isPreviousCursor = false,
				columnValue = null,
				internalId = null
			)
		)

		companion object {
			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: Int) = FileCountPagination(
				CommonPagination.TokenData(
					SortOrder.FILE_COUNT,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: TagDto) = row.fileCount
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