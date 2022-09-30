package net.termer.twinemedia.util

import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.await
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

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
 * Converts this array into a JsonArray
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