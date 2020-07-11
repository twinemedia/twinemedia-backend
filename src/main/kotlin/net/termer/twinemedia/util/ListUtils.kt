package net.termer.twinemedia.util

object ListTypes {
    const val STANDARD = 0
    const val AUTOMATICALLY_POPULATED = 1
}

/**
 * Returns whether the specified type Int is a valid list type
 * @param typeInt The type Int
 * @return Whether the specified type Int is a valid list type
 * @since 1.0
 */
fun isValidListType(typeInt : Int) : Boolean {
    return typeInt > -1 && typeInt < 2
}