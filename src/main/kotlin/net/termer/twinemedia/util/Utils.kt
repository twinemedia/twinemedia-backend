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