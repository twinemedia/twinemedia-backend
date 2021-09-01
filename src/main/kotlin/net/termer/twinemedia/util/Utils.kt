package net.termer.twinemedia.util

import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.RowSet
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.Twine.domains
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.db.dataobject.SerializableDataObject
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
private val gmtDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")

/**
 * Trims a String down to the specified length
 * @return The String trimmed down to the specified length
 * @since 1.0.0
 */
fun String.toLength(len: Int) = if(this.length > len) this.substring(0, len) else this

/**
 * Returns null if this String is empty, otherwise returns the String
 * @return Null if this String is empty, otherwise this String
 * @since 1.0.0
 */
fun String.nullIfEmpty() = if(this == "") null else this

/**
 * Strips a trailing forward slash from a String if present and returns the processed String
 * @return The processed String
 * @since 1.5.2
 */
fun String.stripTrailingSlash() = if(this.endsWith('/')) this.substring(0, this.length-1) else this

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
		for(str in this.map { it.toLowerCase() })
			if(str.trim().isNotBlank() && !contains(str))
				add(str)
	}.toTypedArray()
}

/**
 * Returns the hostnames this application should bind its routes to
 * @return The hostnames this application should bind its routes to
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun appHostnames(): Array<String> = if(config.domain != "*")
	domains().byNameOrDefault(config.domain).hostnames()
else
	arrayOf("*")

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
	name = name[0].toUpperCase() + name.substring(1)

	return name.trim()
}

/**
 * Converts this JSON array to an array of Strings, either by taking Strings in it and adding them, or calling toString() on elements in the array
 * @return This JSON array as an array of Strings
 * @since 1.4.0
 */
fun JsonArray.toStringArray() = ArrayList<String>(size()).apply {
	for(item in list)
		if(item != null)
			add(item.toString())
}.toTypedArray()

/**
 * Converts this array into a JsonArray
 * @return This array as a JsonArray
 * @since 1.4.0
 */
fun Array<String>.toJsonArray() = JsonArray().also { json ->
	for(str in this)
	    json.add(str)
}

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

/**
 * Converts a RowSet of serializable rows to a JSON array containing the JSON serialized version of the RowSet's rows
 * @return A JSON array containing the JSON serialized versions of the RowSet's rows
 * @since 1.5.0
 */
fun RowSet<out SerializableDataObject>.toJsonArray(): JsonArray {
	val arr = JsonArray(ArrayList<Any?>(rowCount()))

	for(row in this)
		arr.add(row.toJson())

	return arr
}