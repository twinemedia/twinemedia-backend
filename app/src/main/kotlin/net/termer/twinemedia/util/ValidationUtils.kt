package net.termer.twinemedia.util

import java.util.regex.Pattern

/**
 * Regex pattern that matches email addresses
 * @since 2.0.0
 */
val emailPattern: Pattern = Pattern.compile("^[\\w.]+@[a-zA-Z_\\d.-]+?\\.[a-zA-Z\\d-]{2,63}$")

/**
 * Returns whether the provided email is valid
 * @return Whether the provided email is valid
 * @since 2.0.0
 */
fun isEmailValid(email: String) = emailPattern.matcher(email).matches()