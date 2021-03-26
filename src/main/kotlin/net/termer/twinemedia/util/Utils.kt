package net.termer.twinemedia.util

import io.vertx.core.json.JsonArray
import net.termer.twine.Twine.domains
import net.termer.twinemedia.Module.Companion.config
import java.text.SimpleDateFormat
import java.util.*

private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

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
	if(filename.lastIndexOf('.') > 0)
		name = filename.substring(0, filename.lastIndexOf('.'))

	// Replace underscores and dashes with spaces with spaces
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

