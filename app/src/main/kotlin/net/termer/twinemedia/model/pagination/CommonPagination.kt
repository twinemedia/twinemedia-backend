package net.termer.twinemedia.model.pagination

import io.vertx.ext.web.validation.RequestParameters
import io.vertx.sqlclient.Row
import net.termer.twinemedia.dataobject.StandardRow
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.db.fetchManyAwait
import org.jooq.Field
import org.jooq.SelectQuery
import org.jooq.impl.DSL.*
import java.nio.ByteBuffer
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.time.OffsetDateTime
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import kotlin.text.Charsets.UTF_8

/**
 * A common pagination object constructor function
 * @since 2.0.0
 */
typealias CommonPaginationConstructor<TRow, TSortEnum, TColType> = (
	isSortedByDesc: Boolean,
	isPreviousCursor: Boolean,
	internalId: Int,
	columnValue: TColType
) -> RowPagination<TRow, TSortEnum, TColType>

/**
 * A common pagination row column accessor
 * @since 2.0.0
 */
typealias CommonRowColumnAccessor<TRow, TColType> = (row: TRow) -> TColType

/**
 * A common pagination row mapper
 * @since 2.0.0
 */
typealias CommonRowMapper<TRow> = (row: Row) -> TRow

/**
 * Abstract classes for common pagination types
 * @since 2.0.0
 */
object CommonPagination {
	private class TokenCommonParts<TSortEnum: Enum<TSortEnum>>(
		val sortEnum: TSortEnum,
		val isSortedByDesc: Boolean,
		val isPreviousCursor: Boolean,
		val internalId: Int?,
		val finalBytes: ByteArray
	)

	/**
	 * Data extracted from a common pagination token
	 * @since 2.0.0
	 */
	data class TokenData<TEnum: Enum<TEnum>, TColType>(
		/**
		 * The sorting enum value
		 * @since 2.0.0
		 */
		val sortEnum: TEnum,

		/**
		 * The isSortedByDesc value
		 * @since 2.0.0
		 */
		val isSortedByDesc: Boolean,

		/**
		 * The isPreviousCursor value
		 * @since 2.0.0
		 */
		val isPreviousCursor: Boolean,

		/**
		 * The internalId value
		 * @since 2.0.0
		 */
		val internalId: Int?,

		/**
		 * The columnValue value
		 * @since 2.0.0
		 */
		val columnValue: TColType?
	)

	/**
	 * A generic row column accessor for a creation timestamp
	 * @param row The row
	 * @return The creation timestamp
	 * @since 2.0.0
	 */
	fun rowCreatedTsAccessor(row: StandardRow) = row.createdTs

	/**
	 * A generic row column accessor for a last modified timestamp
	 * @param row The row
	 * @return The last modified timestamp
	 * @since 2.0.0
	 */
	fun rowModifiedTsAccessor(row: StandardRow) = row.modifiedTs

	/**
	 * Decodes a sorting enum from a [ByteArray]
	 * @param T The enum type
	 * @param bytes The bytes to decode
	 * @param enumVals The values for the sorting enum
	 * @return The decoded enum
	 * @throws PaginationTokenDecodeException If decoding the enum fails
	 */
	fun <T: Enum<T>> decodeSortEnum(bytes: ByteArray, enumVals: Array<T>, offset: Int = 0): T {
		val enumInt = intFromBytes(bytes, offset)

		if(enumInt < 0 || enumInt >= enumVals.size)
			throw PaginationTokenDecodeException("Invalid sorting enum int $enumInt")

		return enumVals[enumInt]
	}

	/**
	 * Creates a default [RowPagination] instance without a cursor using the provided [TokenData] transformer function and default order
	 * @param defaultOrder The default sort order to use
	 * @param tokenDataToPaginationFunc The [TokenData] transformer function to use
	 * @return The empty [RowPagination] instance
	 */
	inline fun <TRowPag : RowPagination<*, *, *>, reified TOrder : Enum<TOrder>> createDefaultPagination(
		defaultOrder: TOrder,
		tokenDataToPaginationFunc: (tokenData: TokenData<TOrder, *>) -> TRowPag
	) = tokenDataToPaginationFunc(TokenData(
		sortEnum = defaultOrder,
		isSortedByDesc = false,
		isPreviousCursor = false,
		columnValue = null,
		internalId = null
	))

	/**
	 * Resolves a [TRowPag] pagination object based on request parameters.
	 * [TRowPag] is the implementation of [RowPagination] to be used.
	 * [TOrder] is the order enum to resolve the order type from.
	 * @param params The parameters to resolve pagination from
	 * @param defaultOrder The default sort order to use if none is provided in the JSON
	 * @param tokenDataToPaginationFunc The function to transform a [TokenData] object into a [TRowPag] object
	 * @param decodeTokenFunc The function to decode a raw pagination token into a [TRowPag] object
	 * @return The [TRowPag] object
	 * @throws PaginationTokenDecodeException If the token provided (if any) is malformed or decoding it fails for another reason
	 * @since 2.0.0
	 */
	suspend inline fun <TRowPag : RowPagination<*, *, *>, reified TOrder : Enum<TOrder>> resolvePaginationFromParameters(
		params: RequestParameters,
		defaultOrder: TOrder,
		tokenDataToPaginationFunc: (tokenData: TokenData<TOrder, *>) -> TRowPag,
		decodeTokenFunc: suspend (token: String) -> TRowPag
	): TRowPag {
		// Check for pagination token
		val paramsObj = params.queryParameter("pagination")?.jsonObject
			?: return createDefaultPagination(defaultOrder, tokenDataToPaginationFunc)

		val pageToken = paramsObj.getString("page")
		if(pageToken == null) {
			// Extract ordering from params
			val orderStr = paramsObj.getString("order")
			val order = if(orderStr == null)
				defaultOrder
			else
				enumByNameOr(orderStr, defaultOrder)

			val orderDesc = paramsObj.getBoolean("orderDesc") == true

			// Create token data without a cursor
			return tokenDataToPaginationFunc(TokenData(
				sortEnum = order,
				isSortedByDesc = orderDesc,
				isPreviousCursor = false,
				columnValue = null,
				internalId = null
			))
		} else {
			// Various exceptions relating to token decryption can be discarded because they are indicative of a malformed or otherwise invalid pagination token.


			// termer 2023/03/02:
			// As horribly repetitive as the below code is, it's the only performant way to return an empty response without using Lazy (which would result in a heap allocation that most of the time will be unused).
			// I would use a local function, but those aren't supported in inline functions, so I'm stuck with exposing an effectively internal function publicly.
			// Ironically, catching different types of exceptions in one catch block is a convenience that Java has but Kotlin removed.
			// Kotlin makes some bad decisions in terms of usability for the sake of "readability", such as removing the ternary operator (but adding the "elvis operator", which is just a weird syntax for what other languages call the "null coalescing operator").
			// This is the type of thing that soured me on Go, and I'm unhappy everytime I see Kotlin reducing usability for the vague concept of "readability", something that tends to slow down developers who know what they're doing.
			return try {
				decodeTokenFunc(pageToken)
			} catch (e: IllegalArgumentException) {
				createDefaultPagination(defaultOrder, tokenDataToPaginationFunc)
			} catch (e: InvalidKeyException) {
				createDefaultPagination(defaultOrder, tokenDataToPaginationFunc)
			} catch (e: InvalidAlgorithmParameterException) {
				createDefaultPagination(defaultOrder, tokenDataToPaginationFunc)
			} catch (e: IllegalBlockSizeException) {
				createDefaultPagination(defaultOrder, tokenDataToPaginationFunc)
			} catch (e: BadPaddingException) {
				createDefaultPagination(defaultOrder, tokenDataToPaginationFunc)
			}
		}
	}

	private fun <TSortEnum: Enum<TSortEnum>> decodeTokenCommonParts(bytes: ByteArray, sortEnumValues: Array<TSortEnum>, sortEnumVal: TSortEnum? = null): TokenCommonParts<TSortEnum> {
		// Deserialize token contents
		val sortEnum = sortEnumVal ?: decodeSortEnum(bytes, sortEnumValues)
		val isSortedByDesc = bytes[4] == (1).toByte()
		val isPreviousCursor = bytes[5] == (1).toByte()
		var internalId: Int? = intFromBytes(bytes, 6)
		if(internalId!! < 0)
			internalId = null

		return TokenCommonParts(
			sortEnum,
			isPreviousCursor,
			isSortedByDesc,
			internalId,
			finalBytes = bytes.copyOfRange(10, bytes.size)
		)
	}

	/**
	 * The base abstract class for common pagination implementations
	 * @since 2.0.0
	 */
	abstract class Base<TRow: StandardRow, TSortEnum: Enum<TSortEnum>, TColType>(
		private val colValField: Field<Any>,
		private val internalIdField: Field<Any>,
		private val constructor: CommonPaginationConstructor<TRow, TSortEnum, TColType>,
		private val rowColumnAccessor: CommonRowColumnAccessor<TRow, TColType>
	) : RowPagination<TRow, TSortEnum, TColType> {
		override suspend fun fetch(
			query: SelectQuery<*>,
			limit: Int,
			mapper: CommonRowMapper<TRow>
		): RowPagination.Results<TRow, TSortEnum, TColType> {
			// Apply pagination on query
			val useLtAndDesc = isSortedByDesc.oppositeIf(isPreviousCursor)
			val hasCursor = columnValue != null && internalId != null
			if (hasCursor) {
				val row = row(colValField, internalIdField)

				query.addConditions(
					if (useLtAndDesc)
						row.lt(row(columnValue, internalId))
					else
						row.gt(row(columnValue, internalId))
				)
			}

			// Order the query
			if (useLtAndDesc)
				query.addOrderBy(colValField.desc(), internalIdField.desc())
			else
				query.addOrderBy(colValField, internalIdField)

			// Fetch one more row than we need so that we can determine whether there's a prev/next page
			query.addLimit(limit + 1)

			// Fetch results
			val rows = query.fetchManyAwait().map(mapper)

			// No need to do anything if no rows were returned; return an empty result object
			if (rows.isEmpty()) {
				return RowPagination.Results(
					results = rows,
					prevPage = null,
					nextPage = null,
				)
			}

			// Reverse rows if this is the result of a previous cursor query
			val res = if (isPreviousCursor)
				rows.asReversed()
			else
				rows

			// The number of rows to return (necessary because we're fetching one extra row)
			val returnCount = rows.size.coerceAtMost(limit)

			// Determine whether there are prev/next pages based on the returned results length and presence of a cursor
			val hasPrevPage: Boolean
			val hasNextPage: Boolean
			if (isPreviousCursor) {
				hasPrevPage = res.size > limit
				hasNextPage = hasCursor && res.isNotEmpty()
			} else {
				hasPrevPage = hasCursor && res.isNotEmpty()
				hasNextPage = res.size > limit
			}

			// Resolve prev and next pagination values
			val prevPage = if (hasPrevPage) {
				val row = res[if (isPreviousCursor) 1 else 0]
				constructor(isSortedByDesc, true, row.internalId, rowColumnAccessor(row))
			} else {
				null
			}
			val nextPage = if (hasNextPage) {
				val row = res[returnCount - 1]
				constructor(isSortedByDesc, false, row.internalId, rowColumnAccessor(row))
			} else {
				null
			}

			return RowPagination.Results(
				results =
					if (isPreviousCursor)
						res.slice(1 until res.size)
					else
						res.slice(0 until returnCount),

				prevPage = prevPage,
				nextPage = nextPage,
			)
		}

		protected abstract fun serializeColumnValue(): ByteArray

		override suspend fun toToken(): String {
			val colBytes = serializeColumnValue()
			val buf = ByteBuffer.allocate(4 + 1 + 1 + 4 + colBytes.size)

			buf.putInt(sortType.ordinal)
			buf.put(if(isSortedByDesc) 1 else 0)
			buf.put(if(isPreviousCursor) 1 else 0)
			buf.putInt(internalId ?: -1)
			buf.put(colBytes)

			return CryptoService.INSTANCE.aesEncrypt(buf.array())
		}
	}

	/**
	 * Abstract timestamp pagination class to be extended by classes for specific tables and sorting orders
	 * @since 2.0.0
	 */
	abstract class Timestamp<TRow: StandardRow, TSortEnum: Enum<TSortEnum>>(
		timestampField: Field<Any>,
		internalIdField: Field<Any>,
		constructor: CommonPaginationConstructor<TRow, TSortEnum, OffsetDateTime>,
		rowColumnAccessor: CommonRowColumnAccessor<TRow, OffsetDateTime>
	): Base<TRow, TSortEnum, OffsetDateTime>(timestampField, internalIdField, constructor, rowColumnAccessor) {
		companion object {
			/**
			 * Decodes a timestamp pagination token's contents
			 * @param TSortEnum The type of sorting enum contained in the token
			 * @param bytes The token bytes to decode
			 * @param sortEnumValues The values for the sorting enum type contained in the token
			 * @param sortEnumVal The already-resolved sorting enum value. Skips decoding and checking enum int if not null. (defaults to null)
			 * @return The decoded token data
			 * @throws PaginationTokenDecodeException If decoding the token fails
			 * @since 2.0.0
			 */
			fun <TSortEnum: Enum<TSortEnum>> decodeTokenBytes(bytes: ByteArray, sortEnumValues: Array<TSortEnum>, sortEnumVal: TSortEnum? = null): TokenData<TSortEnum, OffsetDateTime> {
				try {
					val commonParts = decodeTokenCommonParts(bytes, sortEnumValues, sortEnumVal)

					return TokenData(
						commonParts.sortEnum,
						commonParts.isPreviousCursor,
						commonParts.isSortedByDesc,
						commonParts.internalId,
						epochSecondToOffsetDateTime(longFromBytes(commonParts.finalBytes))
					)
				} catch(e: Throwable) {
					// Re-throw wrapped exception
					throw PaginationTokenDecodeException(cause = e)
				}
			}
		}

		override fun serializeColumnValue(): ByteArray = ByteBuffer
			.allocate(8)
			.putLong(columnValue?.toInstant()?.epochSecond ?: -1)
			.array()
	}

	/**
	 * Abstract text pagination class to be extended by classes for specific tables and sorting orders
	 * @since 2.0.0
	 */
	abstract class Text<TRow: StandardRow, TSortEnum: Enum<TSortEnum>>(
		textField: Field<Any>,
		internalIdField: Field<Any>,
		constructor: CommonPaginationConstructor<TRow, TSortEnum, String>,
		rowColumnAccessor: CommonRowColumnAccessor<TRow, String>
	): Base<TRow, TSortEnum, String>(textField, internalIdField, constructor, rowColumnAccessor) {
		companion object {
			/**
			 * Decodes a text pagination token's contents
			 * @param TSortEnum The type of sorting enum contained in the token
			 * @param bytes The token bytes to decode
			 * @param sortEnumValues The values for the sorting enum type contained in the token
			 * @param sortEnumVal The already-resolved sorting enum value. Skips decoding and checking enum int if not null. (defaults to null)
			 * @return The decoded token data
			 * @throws PaginationTokenDecodeException If decoding the token fails
			 * @since 2.0.0
			 */
			fun <TSortEnum: Enum<TSortEnum>> decodeTokenBytes(bytes: ByteArray, sortEnumValues: Array<TSortEnum>, sortEnumVal: TSortEnum? = null): TokenData<TSortEnum, String> {
				try {
					val commonParts = decodeTokenCommonParts(bytes, sortEnumValues, sortEnumVal)

					return TokenData(
						commonParts.sortEnum,
						commonParts.isPreviousCursor,
						commonParts.isSortedByDesc,
						commonParts.internalId,
						String(commonParts.finalBytes, UTF_8).nullIfEmpty()
					)
				} catch(e: Throwable) {
					// Re-throw wrapped exception
					throw PaginationTokenDecodeException(cause = e)
				}
			}
		}

		override fun serializeColumnValue(): ByteArray = (columnValue ?: "").toByteArray(UTF_8)
	}

	/**
	 * Abstract integer pagination class to be extended by classes for specific tables and sorting orders
	 * @since 2.0.0
	 */
	abstract class Integer<TRow: StandardRow, TSortEnum: Enum<TSortEnum>>(
		intField: Field<Any>,
		internalIdField: Field<Any>,
		constructor: CommonPaginationConstructor<TRow, TSortEnum, Int>,
		rowColumnAccessor: CommonRowColumnAccessor<TRow, Int>
	): Base<TRow, TSortEnum, Int>(intField, internalIdField, constructor, rowColumnAccessor) {
		companion object {
			/**
			 * Decodes an integer pagination token's contents
			 * @param TSortEnum The type of sorting enum contained in the token
			 * @param bytes The token bytes to decode
			 * @param sortEnumValues The values for the sorting enum type contained in the token
			 * @param sortEnumVal The already-resolved sorting enum value. Skips decoding and checking enum int if not null. (defaults to null)
			 * @return The decoded token data
			 * @throws PaginationTokenDecodeException If decoding the token fails
			 * @since 2.0.0
			 */
			fun <TSortEnum: Enum<TSortEnum>> decodeTokenBytes(bytes: ByteArray, sortEnumValues: Array<TSortEnum>, sortEnumVal: TSortEnum? = null): TokenData<TSortEnum, Int> {
				try {
					val commonParts = decodeTokenCommonParts(bytes, sortEnumValues, sortEnumVal)

					val intVal = intFromBytes(commonParts.finalBytes)

					return TokenData(
						commonParts.sortEnum,
						commonParts.isPreviousCursor,
						commonParts.isSortedByDesc,
						commonParts.internalId,
						if(intVal == Int.MIN_VALUE) null else intVal
					)
				} catch(e: Throwable) {
					// Re-throw wrapped exception
					throw PaginationTokenDecodeException(cause = e)
				}
			}
		}

		override fun serializeColumnValue(): ByteArray = ByteBuffer
			.allocate(4)
			.putInt(columnValue ?: Int.MIN_VALUE)
			.array()
	}

	/**
	 * Abstract long integer pagination class to be extended by classes for specific tables and sorting orders
	 * @since 2.0.0
	 */
	abstract class LongInteger<TRow: StandardRow, TSortEnum: Enum<TSortEnum>>(
		longField: Field<Any>,
		internalIdField: Field<Any>,
		constructor: CommonPaginationConstructor<TRow, TSortEnum, Long>,
		rowColumnAccessor: CommonRowColumnAccessor<TRow, Long>
	): Base<TRow, TSortEnum, Long>(longField, internalIdField, constructor, rowColumnAccessor) {
		companion object {
			/**
			 * Decodes a long integer pagination token's contents
			 * @param TSortEnum The type of sorting enum contained in the token
			 * @param bytes The token bytes to decode
			 * @param sortEnumValues The values for the sorting enum type contained in the token
			 * @param sortEnumVal The already-resolved sorting enum value. Skips decoding and checking enum int if not null. (defaults to null)
			 * @return The decoded token data
			 * @throws PaginationTokenDecodeException If decoding the token fails
			 * @since 2.0.0
			 */
			fun <TSortEnum: Enum<TSortEnum>> decodeTokenBytes(bytes: ByteArray, sortEnumValues: Array<TSortEnum>, sortEnumVal: TSortEnum? = null): TokenData<TSortEnum, Long> {
				try {
					val commonParts = decodeTokenCommonParts(bytes, sortEnumValues, sortEnumVal)

					val longVal = longFromBytes(commonParts.finalBytes)

					return TokenData(
						commonParts.sortEnum,
						commonParts.isPreviousCursor,
						commonParts.isSortedByDesc,
						commonParts.internalId,
						if(longVal == Long.MIN_VALUE) null else longVal
					)
				} catch(e: Throwable) {
					// Re-throw wrapped exception
					throw PaginationTokenDecodeException(cause = e)
				}
			}
		}

		override fun serializeColumnValue(): ByteArray = ByteBuffer
			.allocate(4)
			.putLong(columnValue ?: Long.MIN_VALUE)
			.array()
	}
}