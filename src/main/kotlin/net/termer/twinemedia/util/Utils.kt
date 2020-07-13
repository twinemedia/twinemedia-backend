package net.termer.twinemedia.util

import net.termer.twine.Twine.domains
import net.termer.twinemedia.Module.Companion.config

/**
 * Trims a String down to the specified length
 * @return The String trimmed down to the specified length
 * @since 1.0
 */
fun String.toLength(len : Int) = if(this.length > len) this.substring(0, len) else this
/**
 * Returns null if this String is empty, otherwise returns the String
 * @return Null if this String is empty, otherwise this String
 * @since 1.0
 */
fun String.nullIfEmpty() = if(this == "") null else this

/**
 * Returns the domain this application should bind its routes to
 * @return The domain this application should bind its routes to
 * @since 1.0
 */
fun appDomain() : String {
    var domain = "*"
    if(config.domain != "*") {
        val dom = domains().byName(config.domain).domain()

        if(dom != "default") {
            domain = dom
        }
    }

    return domain
}

/**
 * Formats a filename as a title, stripping out extension, trimming, and replacing underscores and dashes with spaces
 * @param filename The filename to format
 * @return The filename formatted as a title
 * @since 1.0
 */
fun filenameToTitle(filename : String) : String {
    var name = filename

    // Cut off extension if present
    if(filename.lastIndexOf('.') > 0)
        name = filename.substring(0, filename.lastIndexOf('.'))

    // Replace underscores and dashes with spaces with spaces
    name = name
            .replace('_', ' ')
            .replace('-', ' ')

    // Capitalize first letter
    name = name[0].toUpperCase()+name.substring(1)

    return name.trim()
}