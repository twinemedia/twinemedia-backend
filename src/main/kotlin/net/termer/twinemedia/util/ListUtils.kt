package net.termer.twinemedia.util

import net.termer.twinemedia.enumeration.ListType
import net.termer.twinemedia.enumeration.ListVisibility

/**
 * Returns whether the specified type Int is a valid list type
 * @param typeInt The type Int
 * @return Whether the specified type Int is a valid list type
 * @since 1.0
 */
fun isValidListTypeInt(typeInt: Int): Boolean {
    return typeInt > -1 && typeInt < ListType.values().size
}

/**
 * Returns the appropriate ListType object for the provided type Int, or null if it is not valid
 * @param typeInt The type Int
 * @return The appropriate ListType object for the provided type Int, or null if it is not valid
 * @since 1.4.0
 */
fun intToListType(typeInt: Int): ListType? {
    return if(isValidListTypeInt(typeInt))
        ListType.values()[typeInt]
    else
        null
}

/**
 * Returns whether the specified visibility Int is a valid list visibility
 * @param visisbilityInt The visibility Int
 * @return Whether the specified visibility Int is a valid visibility type
 * @since 1.0
 */
fun isValidListVisibilityInt(visibilityInt: Int): Boolean {
    return visibilityInt > -1 && visibilityInt < ListVisibility.values().size
}

/**
 * Returns the appropriate ListVisibility object for the provided visibility Int, or null if it is not valid
 * @param visibilityInt The visibility Int
 * @return The appropriate ListVisibility object for the provided visibility Int, or null if it is not valid
 * @since 1.4.0
 */
fun intToListVisibility(visibilityInt: Int): ListVisibility? {
    return if(isValidListVisibilityInt(visibilityInt))
        ListVisibility.values()[visibilityInt]
    else
        null
}