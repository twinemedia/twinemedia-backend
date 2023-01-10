package net.termer.twinemedia.model.pagination

import io.vertx.sqlclient.Row
import net.termer.twinemedia.dataobject.StandardRow
import net.termer.twinemedia.service.CryptoService
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.db.fetchManyAwait
import org.jooq.Field
import org.jooq.SelectQuery
import org.jooq.impl.DSL.*
import java.nio.ByteBuffer
import java.time.OffsetDateTime
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
	): RowPagination<TRow, TSortEnum, TColType> {
		override suspend fun fetch(
			query: SelectQuery<*>,
			limit: Int,
			mapper: CommonRowMapper<TRow>
		): RowPagination.Results<TRow, TSortEnum, TColType> {
			// Apply pagination on query
			if(columnValue != null && internalId != null) {
				val row = row(colValField, internalIdField)

				query.addConditions(
					if (isSortedByDesc.oppositeIf(isPreviousCursor))
						row.lt(row(columnValue, internalId))
					else
						row.gt(row(columnValue, internalId))
				)
			}
			query.addLimit(limit)
			if(isSortedByDesc)
				query.addOrderBy(colValField.desc(), internalIdField.desc())
			else
				query.addOrderBy(colValField, internalIdField)

			// Fetch results
			val rows = query.fetchManyAwait().map(mapper)
			val res = if(isPreviousCursor)
				rows.asReversed()
			else
				rows

			val first = res.first()
			val last = if(res.size < limit) null else res.last()

			return RowPagination.Results(
				results = res,
				prevPage = constructor(isSortedByDesc, true, first.internalId, rowColumnAccessor(first)),
				nextPage = if(last == null) null else constructor(isSortedByDesc, false, last.internalId, rowColumnAccessor(last))
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