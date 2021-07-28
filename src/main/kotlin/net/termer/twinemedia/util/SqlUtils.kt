package net.termer.twinemedia.util

/**
 * Returns an SQL WHERE condition containing the following conditions
 * @param condition The condition(s) to use
 * @return An SQL WHERE condition containing the following conditions
 * @since 1.5.0
 */
fun composeWhere(vararg condition: String): String {
	return if(condition.isNotEmpty())
		condition.joinToString(" AND ")
	else
		"TRUE"
}