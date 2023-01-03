package net.termer.twinemedia.util

import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.CompletableFuture

private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
private val gmtDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")

/**
 * Creates an ISO date String that represents this Date object
 * @return An ISO date String representing this Date object
 * @since 1.2.0
 */
fun Date.toISOString(): String = simpleDateFormat.format(this)

/**
 * Returns whether this array contains the provided permission
 * @param permission The permission to check for
 * @since 1.3.0
 */
fun Array<String>.containsPermission(permission: String): Boolean {
	var has = false

	// Check if the user has the permission
	if(contains(permission) || contains("$permission.all") || contains("*")) {
		has = true
	} else if(permission.contains('.')) {
		// Check permission tree
		val perm = StringBuilder()
		for(child in permission.split('.')) {
			perm.append("$child.")
			for(p in this)
				if(p == "$perm*") {
					has = true
					break
				}
			if(has)
				break
		}
	}

	return has
}

/**
 * Sets all entries to lowercase and removes duplicates and blanks, then returns the result
 * @return An array of lowercase Strings without any duplicates or blanks
 * @since 1.4.0
 */
fun Array<String>.removeDuplicatesBlanksAndToLowercase(): Array<String> {
	return ArrayList<String>().apply {
		for(str in this.map { it.lowercase() })
			if(str.trim().isNotBlank() && !contains(str))
				add(str)
	}.toTypedArray()
}

/**
 * Formats a filename as a title, stripping out extension, trimming, and replacing underscores and dashes with spaces
 * @param filename The filename to format
 * @return The filename formatted as a title
 * @since 1.0.0
 */
fun filenameToTitle(filename: String): String {
	var name = filename

	// Cut off extension if present
	val extIndex = filename.lastIndexOf('.')
	if(extIndex > 0)
		name = filename.substring(0, extIndex)

	// Replace underscores and dashes with spaces
	name = name
			.replace('_', ' ')
			.replace(Regex("-(?! )"), " ")

	// Capitalize first letter
	name = name[0].uppercaseChar() + name.substring(1)

	return name.trim()
}

/**
 * Converts this JSON array to an array of Strings, either by taking Strings in it and adding them, or calling toString() on elements in the array.
 * Null values are skipped.
 * @return This JSON array as an array of Strings
 * @since 1.4.0
 */
fun JsonArray.toStringArray() = ArrayList<String>(size()).apply {
	for(item in list)
		if(item != null)
			add(item.toString())
}.toTypedArray()

/**
 * Converts this JSON array to an array of Ints, either by taking Ints in it and adding them, or calling toInt() on elements in the array.
 * Null values are skipped.
 * @return This JSON array as an array of Ints
 * @since 1.4.0
 */
fun JsonArray.toIntArray() = ArrayList<Int>(size()).apply {
	for(item in list) {
		if(item is Number)
			add(item.toInt())
		else if(item is String)
			add(item.toInt())
	}
}.toTypedArray()

/**
 * Converts this array of JsonSerializable objects into a JsonArray
 * @return This array as a JsonArray
 * @since 2.0.0
 */
fun Array<out JsonSerializable>.toJsonArray(): JsonArray {
	val jsonArr = JsonArray(ArrayList<JsonObject>(size))

	for(item in this)
		jsonArr.add(item.toJson())

	return jsonArr
}

/**
 * Converts this array into a JsonArray.
 * Note that this method does not recursively convert objects within the array, and therefore should only be used for arrays of JSON-compatible types.
 * @return This array as a JsonArray
 * @since 1.4.0
 */
fun Array<*>.toJsonArray() = JsonArray(toList())

/**
 * Takes a filename and adds a unix timestamp after the name, doing any necessary trimming to stay in the filename length limit
 * @param filename The filename to process
 * @return The filename processed with a unix timestamp
 * @since 1.5.0
 */
fun filenameToFilenameWithUnixTimestamp(filename: String): String {
	val unixTime = "${System.currentTimeMillis()/1000L}"

	val dotIndex = filename.lastIndexOf('.')
	val name = if(dotIndex == -1) filename else filename.substring(0, dotIndex)
	val ext = if(dotIndex == -1) "" else filename.substring(dotIndex)
	var nameWithTime = "$name-$unixTime"

	// Make sure name and extension aren't too long, trim if so
	val totalLen = nameWithTime.length+ext.length
	if(totalLen > 256)
		nameWithTime = name.substring(0, name.lastIndex-(totalLen-256))+'-'+unixTime

	return nameWithTime+ext
}

/**
 * Suspends under this future completes, or throws an exception if it failed
 * @return The future's result
 * @since 1.5.0
 */
suspend fun <T> CompletableFuture<T>.await(): T {
	val promise = Promise.promise<T>()

	whenComplete { t, throwable ->
		if(throwable == null)
			promise.complete(t)
		else
			promise.fail(throwable)
	}

	return promise.future().await()
}

/**
 * Converts the provided OffsetDateTime to a GMT time string
 * @param date The OffsetDateTime to convert
 * @return The provided OffsetDateTime to a GMT time string
 * @since 1.5.0
 */
fun offsetDateTimeToGMT(date: OffsetDateTime): String = gmtDateFormat.format(Date.from(date.toInstant()))

/**
 * Converts the provided GMT time string to an OffsetDateTime
 * @param gmtTime The GMT time string to convert
 * @return The provided GMT time string to an OffsetDateTime
 * @since 1.5.2
 */
fun gmtToOffsetDateTime(gmtTime: String): OffsetDateTime = gmtDateFormat.parse(gmtTime).toInstant().atOffset(ZoneOffset.UTC)

///**
// * Converts a RowSet of serializable rows to a JSON array containing the JSON serialized version of the RowSet's rows
// * @return A JSON array containing the JSON serialized versions of the RowSet's rows
// * @since 1.5.0
// */
//fun RowSet<out SerializableDataObject>.toJsonArray(): JsonArray {
//	val arr = JsonArray(ArrayList<Any?>(rowCount()))
//
//	for(row in this)
//		arr.add(row.toJson())
//
//	return arr
//}

/**
 * Returns the greatest number in this iterable
 * @return The greatest number in this iterable
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
fun <T: Number> Iterable<Comparable<T>>.greatest(): T? {
	var res: T? = null

	for(num in this)
		if(res == null || num > res)
			res = num as T

	return res
}

/**
 * Returns the least number in this iterable
 * @return The least number in this iterable
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
fun <T: Number> Iterable<Comparable<T>>.least(): T? {
	var res: T? = null

	for(num in this)
		if(res == null || num < res)
			res = num as T

	return res
}

/**
 * Returns this boolean's opposite value if the provided boolean is true
 * @return This boolean's opposite value if the provided boolean is true
 * @since 2.0.0
 */
fun Boolean.oppositeIf(otherBool: Boolean) = if(otherBool) !this else this

/**
 * Converts an integer into its underlying bytes.
 * Uses big endian encoding.
 * @return The bytes representing the int
 * @since 2.0.0
 */
fun Int.toBytes() = byteArrayOf(
	(this shr 24).toByte(),
	(this shr 16).toByte(),
	(this shr 8).toByte(),
	this.toByte()
)

/**
 * Converts a long integer into its underlying bytes.
 * Uses big endian encoding.
 * @return The bytes representing the long
 * @since 2.0.0
 */
fun Long.toBytes() = byteArrayOf(
	(this shr 56).toByte(),
	(this shr 48).toByte(),
	(this shr 40).toByte(),
	(this shr 32).toByte(),
	(this shr 24).toByte(),
	(this shr 16).toByte(),
	(this shr 8).toByte(),
	this.toByte()
)

/**
 * Converts a byte array to an integer.
 * Uses big endian encoding.
 * @param bytes The bytes to read
 * @param offset The offset from which to read (defaults to 0)
 * @return The int
 * @since 2.0.0
 */
fun intFromBytes(bytes: ByteArray, offset: Int = 0): Int {
	var res = 0

	// Iterating through for loop
	for (i in offset until offset + 4)
		res = (res shl 8) + (bytes[i].toInt() and 255)

	return res
}

/**
 * Converts a byte array to a long integer.
 * Uses big endian encoding.
 * @param bytes The bytes to read
 * @param offset The offset from which to read (defaults to 0)
 * @return The long
 * @since 2.0.0
 */
fun longFromBytes(bytes: ByteArray, offset: Int = 0): Long {
	var res = 0L

	// Iterating through for loop
	for (i in offset until offset+8)
		res = (res shl 8) + (bytes[i].toInt() and 255)

	return res
}

/**
 * Returns the element at the specified index, or a default value if the index is invalid
 * @param index The index
 * @param default The default value to return
 * @return The element at the specified index, or the default value
 * @since 2.0.0
 */
fun <T> Array<T>.getOr(index: Int, default: T) =
	if(index < 0 || index >= this.size)
		default
	else
		this[index]

/**
 * Parses the string into an int, or returns a default value if the string is not a valid int
 * @param default The default value to return
 * @return The int or the default value
 * @since 2.0.0
 */
fun String.toIntOr(default: Int) = toIntOrNull() ?: default

/**
 * Parses the string into a [Some]<[Int]>, or returns [None]<[Int]> if the string is not a valid int
 * @return The int or none
 * @since 2.0.0
 */
fun String.toIntOrNone(): Option<Int> {
	val int = toIntOrNull()
	return if(int == null)
		none<Int>()
	else
		some(int)
}

/**
 * Creates an [OffsetDateTime] using an epoch second.
 * If the epoch second is less than 0, it will return null.
 * @param epoch The epoch second
 * @return The resulting [OffsetDateTime], or null if epoch second is invalid
 * @since 2.0.0
 */
fun epochSecondToOffsetDateTime(epoch: Long): OffsetDateTime? {
	if (epoch < 0)
		return null

	return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
}

/**
 * Parses a date string into an [OffsetDateTime] instance
 * @param str The date string
 * @return The [OffsetDateTime]
 * @throws DateTimeParseException If parsing the date fails (e.g. the string does not represent a valid date)
 * @since 2.0.0
 */
fun dateStringToOffsetDateTime(str: String) = OffsetDateTime.ofInstant(Instant.parse(str), ZoneId.systemDefault())!!

/**
 * Parses a date string into an [OffsetDateTime] instance, or null if the date string is invalid
 * @param str The date string
 * @return The [OffsetDateTime], or null if the date string is invalid
 * @since 2.0.0
 */
fun dateStringToOffsetDateTimeOrNull(str: String) = try {
	dateStringToOffsetDateTime(str)
} catch(e: DateTimeParseException) {
	null
}

/**
 * Parses a date string into an [Option]<[OffsetDateTime]> instance, or [None] if the date string is invalid
 * @param str The date string
 * @return The [Option]<[OffsetDateTime]>, or [None] if the date string is invalid
 * @since 2.0.0
 */
fun dateStringToOffsetDateTimeOrNone(str: String) = try {
	some(dateStringToOffsetDateTime(str))
} catch(e: DateTimeParseException) {
	none()
}

/**
 * Returns [Some]<[T]> or [None]<[T]> if null
 * @return This wrapped in an [Option] object
 * @since 2.0.0
 */
fun <T> T?.orNone(): Option<T> = if(this == null) none() else some(this)