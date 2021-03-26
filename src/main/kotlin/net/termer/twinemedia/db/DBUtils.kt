package net.termer.twinemedia.db

import io.vertx.sqlclient.Row

/**
 * Returns whether the specified column is present in this row
 * @param col The name of the column to check for
 * @return Whether the specified column is present in this row
 * @since 1.4.0
 */
fun Row.containsColumn(col: String) = getColumnIndex(col) != -1