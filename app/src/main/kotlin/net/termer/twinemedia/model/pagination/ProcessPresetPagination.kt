package net.termer.twinemedia.model.pagination

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.validation.RequestParameters
import net.termer.twinemedia.dataobject.ProcessPresetDto
import net.termer.twinemedia.model.ProcessPresetsModel.*
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.util.*
import org.jooq.impl.DSL.*
import java.time.OffsetDateTime

/**
 * Abstract class for process preset pagination implementations
 * @since 2.0.0
 */
interface ProcessPresetPagination<TColType>: RowPagination<ProcessPresetDto, SortOrder, TColType> {
	companion object {
		@Suppress("UNCHECKED_CAST")
		private fun toPagination(tokenData: CommonPagination.TokenData<SortOrder, *>): ProcessPresetPagination<*> {
			return when(tokenData.sortEnum) {
				SortOrder.CREATED_TS ->
					CreatedTsPagination(tokenData as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.MODIFIED_TS ->
					ModifiedTsPagination(tokenData as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.NAME_ALPHABETICALLY ->
					NamePagination(tokenData as CommonPagination.TokenData<SortOrder, String>)
				SortOrder.MIME_ALPHABETICALLY ->
					MimePagination(tokenData as CommonPagination.TokenData<SortOrder, String>)
			}
		}

		/**
		 * Decodes a process preset pagination token into an [ProcessPresetPagination] object
		 * @param token The token to decode
		 * @return The [ProcessPresetPagination] object
		 * @throws PaginationTokenDecodeException If the token is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun decodeToken(token: String): ProcessPresetPagination<*> {
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
				SortOrder.MIME_ALPHABETICALLY ->
					toPagination(CommonPagination.Text.decodeTokenBytes(bytes, sortEnumVals, sort))
			}
		}

		/**
		 * Resolves an [ProcessPresetPagination] object based on request parameters
		 * @param params The request parameters to resolve pagination from
		 * @return The [ProcessPresetPagination] object
		 * @throws PaginationTokenDecodeException If the token provided (if any) is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun resolvePaginationFromParameters(params: RequestParameters): ProcessPresetPagination<*> {
			return CommonPagination.resolvePaginationFromParameters(params, SortOrder.CREATED_TS, ::toPagination, ::decodeToken)
		}
	}

	/**
	 * Process preset creation timestamp pagination
	 * @since 2.0.0
	 */
	class CreatedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): ProcessPresetPagination<OffsetDateTime>, CommonPagination.Timestamp<ProcessPresetDto, SortOrder>(
		timestampField = field("process_presets.preset_created_ts"),
		internalIdField = field("process_presets.id"),
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
	 * Process preset last modified timestamp pagination
	 * @since 2.0.0
	 */
	class ModifiedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): ProcessPresetPagination<OffsetDateTime>, CommonPagination.Timestamp<ProcessPresetDto, SortOrder>(
		timestampField = field("process_presets.preset_modified_ts"),
		internalIdField = field("process_presets.id"),
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
	 * Process preset name pagination
	 * @since 2.0.0
	 */
	class NamePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): ProcessPresetPagination<String>, CommonPagination.Text<ProcessPresetDto, SortOrder>(
		textField = field("process_presets.preset_name"),
		internalIdField = field("process_presets.id"),
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

			fun rowAccessor(row: ProcessPresetDto) = row.name
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
	 * Process preset MIME pagination
	 * @since 2.0.0
	 */
	class MimePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): ProcessPresetPagination<String>, CommonPagination.Text<ProcessPresetDto, SortOrder>(
		textField = field("process_presets.preset_mime"),
		internalIdField = field("process_presets.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		/**
		 * Creates a no-cursor [MimePagination] object
		 * @param sortDesc Whether results will be returned in descending order
		 * @return The [MimePagination] instance
		 * @since 2.0.0
		 */
		fun create(sortDesc: Boolean) = MimePagination(
			CommonPagination.TokenData(
				sortEnum = SortOrder.MIME_ALPHABETICALLY,
				isSortedByDesc = sortDesc,
				isPreviousCursor = false,
				columnValue = null,
				internalId = null
			)
		)

		companion object {
			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: String) = MimePagination(
				CommonPagination.TokenData(
					SortOrder.MIME_ALPHABETICALLY,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: ProcessPresetDto) = row.mime
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
}