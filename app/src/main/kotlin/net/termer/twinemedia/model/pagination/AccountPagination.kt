package net.termer.twinemedia.model.pagination

import io.vertx.ext.web.validation.RequestParameters
import net.termer.twinemedia.dataobject.AccountDto
import net.termer.twinemedia.model.AccountsModel.*
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.util.*
import org.jooq.impl.DSL.*
import java.time.OffsetDateTime

/**
 * Abstract class for account pagination implementations
 * @since 2.0.0
 */
interface AccountPagination<TColType>: RowPagination<AccountDto, SortOrder, TColType> {
	companion object {
		@Suppress("UNCHECKED_CAST")
		private fun toPagination(tokenData: CommonPagination.TokenData<SortOrder, *>): AccountPagination<*> {
			return when(tokenData.sortEnum) {
				SortOrder.CREATED_TS ->
					CreatedTsPagination(tokenData as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.MODIFIED_TS ->
					ModifiedTsPagination(tokenData as CommonPagination.TokenData<SortOrder, OffsetDateTime>)
				SortOrder.NAME_ALPHABETICALLY ->
					NamePagination(tokenData as CommonPagination.TokenData<SortOrder, String>)
				SortOrder.EMAIL_ALPHABETICALLY ->
					EmailPagination(tokenData as CommonPagination.TokenData<SortOrder, String>)
				SortOrder.FILE_COUNT ->
					FileCountPagination(tokenData as CommonPagination.TokenData<SortOrder, Int>)
			}
		}

		/**
		 * Decodes an account pagination token into an [AccountPagination] object
		 * @param token The token to decode
		 * @return The [AccountPagination] object
		 * @throws PaginationTokenDecodeException If the token is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun decodeToken(token: String): AccountPagination<*> {
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
				SortOrder.EMAIL_ALPHABETICALLY ->
					toPagination(CommonPagination.Text.decodeTokenBytes(bytes, sortEnumVals, sort))
				SortOrder.FILE_COUNT ->
					toPagination(CommonPagination.Integer.decodeTokenBytes(bytes, sortEnumVals, sort))
			}
		}

		/**
		 * Resolves an [AccountPagination] object based on request parameters
		 * @param params The request parameters to resolve pagination from
		 * @return The [AccountPagination] object
		 * @throws PaginationTokenDecodeException If the token provided (if any) is malformed or decoding it fails for another reason
		 * @since 2.0.0
		 */
		suspend fun resolvePaginationFromParameters(params: RequestParameters): AccountPagination<*> {
			return CommonPagination.resolvePaginationFromParameters(params, SortOrder.CREATED_TS, ::toPagination, ::decodeToken)
		}
	}

	/**
	 * Account creation timestamp pagination
	 * @since 2.0.0
	 */
	class CreatedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): AccountPagination<OffsetDateTime>, CommonPagination.Timestamp<AccountDto, SortOrder>(
		timestampField = field("accounts.account_created_ts"),
		internalIdField = field("accounts.id"),
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
	 * Account last modified timestamp pagination
	 * @since 2.0.0
	 */
	class ModifiedTsPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, OffsetDateTime>
	): AccountPagination<OffsetDateTime>, CommonPagination.Timestamp<AccountDto, SortOrder>(
		timestampField = field("accounts.account_modified_ts"),
		internalIdField = field("accounts.id"),
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
	 * Account name pagination
	 * @since 2.0.0
	 */
	class NamePagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): AccountPagination<String>, CommonPagination.Text<AccountDto, SortOrder>(
		textField = field("accounts.account_name"),
		internalIdField = field("accounts.id"),
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

			fun rowAccessor(row: AccountDto) = row.name
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
	 * Account email pagination
	 * @since 2.0.0
	 */
	class EmailPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, String>
	): AccountPagination<String>, CommonPagination.Text<AccountDto, SortOrder>(
		textField = field("accounts.account_email"),
		internalIdField = field("accounts.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [EmailPagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [EmailPagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = EmailPagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.EMAIL_ALPHABETICALLY,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: String) = EmailPagination(
				CommonPagination.TokenData(
					SortOrder.EMAIL_ALPHABETICALLY,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: AccountDto) = row.email
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
	 * Account file count pagination
	 * @since 2.0.0
	 */
	class FileCountPagination(
		private val tokenData: CommonPagination.TokenData<SortOrder, Int>
	): AccountPagination<Int>, CommonPagination.Integer<AccountDto, SortOrder>(
		intField = field("accounts.account_file_count"),
		internalIdField = field("accounts.id"),
		constructor = this::constructor,
		rowColumnAccessor = this::rowAccessor
	) {
		companion object {
			/**
			 * Creates a no-cursor [FileCountPagination] object
			 * @param sortDesc Whether results will be returned in descending order
			 * @return The [FileCountPagination] instance
			 * @since 2.0.0
			 */
			fun create(sortDesc: Boolean) = NamePagination(
				CommonPagination.TokenData(
					sortEnum = SortOrder.FILE_COUNT,
					isSortedByDesc = sortDesc,
					isPreviousCursor = false,
					columnValue = null,
					internalId = null
				)
			)

			fun constructor(isSortedByDesc: Boolean, isPreviousCursor: Boolean, internalId: Int, columnValue: Int) = FileCountPagination(
				CommonPagination.TokenData(
					SortOrder.FILE_COUNT,
					isSortedByDesc,
					isPreviousCursor,
					internalId,
					columnValue
				)
			)

			fun rowAccessor(row: AccountDto) = row.fileCount
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