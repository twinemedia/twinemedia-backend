package net.termer.twinemedia.db

import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.Module.Companion.config

private var refreshScheduled = false
private var refreshing = false

/**
 * Reserves a database connection and starts the tags view refresher
 */
suspend fun startTagsViewRefresher() {
	// Create private connection pool
	val connOps = PgConnectOptions()
			.setHost(config.db_address)
			.setPort(config.db_port)
			.setDatabase(config.db_name)
			.setUser(config.db_user)
			.setPassword(config.db_pass)
	// Initialize pool with two connections in case one fails
	val poolOps = PoolOptions()
			.setMaxSize(2)

	// Create and connect
	val client = PgPool.pool(vertx(), connOps, poolOps)

	// Test connection
	val testConn = client.connection.await()
	testConn.query("SELECT 0").execute().await()
	testConn.close().await()

	// Start refresh loop
	vertx().setPeriodic(1000) {
		GlobalScope.launch(vertx().dispatcher()) {
			try {
				if(refreshScheduled && !refreshing) {
					refreshScheduled = false
					refreshing = true

					// Fetch connection
					val conn = client.connection.await()

					// Refresh the view
					conn.query("REFRESH MATERIALIZED VIEW CONCURRENTLY tags").execute().await()

					// Return connection
					conn.close().await()

					// Finished
					refreshing = false
				}
			} catch(e: Exception) {
				refreshing = false

				logger.error("Failed to refresh tags view:")
				e.printStackTrace()
			}
		}
	}
}

/**
 * Schedules a tags view refresh
 * @since 1.4.0
 */
fun scheduleTagsViewRefresh() {
	refreshScheduled = true
}