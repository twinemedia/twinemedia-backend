package net.termer.twinemedia.db

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.*
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location

/**
 * Object containing global database fields
 * @since 1.0.0
 */
object Database {
	var pgClient: PgPool? = null

	/**
	 * A Reference to pgClient
	 * @since 1.4.0
	 */
	val client
			get() = pgClient!!
}

/**
 * Initializes the database connection
 * @since 1.0.0
 */
fun dbInit() {
	// Configuration
	val connOps = PgConnectOptions()
			.setHost(config.db_address)
			.setPort(config.db_port)
			.setDatabase(config.db_name)
			.setUser(config.db_user)
			.setPassword(config.db_pass)
	val poolOps = PoolOptions()
			.setMaxSize(config.db_max_pool_size)

	// Create and connect
	Database.pgClient = PgPool.pool(vertx(), connOps, poolOps)
}

/**
 * Runs database migrations
 * @since 1.0.0
 */
fun dbMigrate() {
	// Create FlyWay instance
	val flyway = Flyway.configure().dataSource(
			"jdbc:postgresql://${config.db_address}:${config.db_port}/${config.db_name}",
			config.db_user,
			config.db_pass
	).locations(Location("twinemedia/db/migration")).load()

	// Run database migrations
	flyway.migrate()
}

/**
 * Closes the database connection
 * @since 1.0.0
 */
fun dbClose() {
	Database.client.close()
}

/**
 * Returns a connection for the database
 * @return A connection for the database
 * @since 1.4.0
 */
suspend fun dbConn(): SqlConnection {
	return Database.client.connection.await()
}