package net.termer.twinemedia.util.db

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.*
import net.termer.twinemedia.AppConfig
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.output.MigrateResult
import org.jooq.SQLDialect
import org.jooq.impl.DSL

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

	/**
	 * The jOOQ DSLContext instance used by DB models
	 * @since 2.0.0
	 */
	val Sql = DSL.using(SQLDialect.POSTGRES)
}

/**
 * Initializes the database connection
 * @since 2.0.0
 */
suspend fun dbInit(vertx: Vertx, config: AppConfig) {
	// Configuration
	val connOps = PgConnectOptions()
		.setHost(config.dbHost)
		.setPort(config.dbPort)
		.setDatabase(config.dbName)
		.setUser(config.dbAuthUser)
		.setPassword(config.dbAuthPassword)
	val poolOps = PoolOptions()
		.setMaxSize(config.dbMaxPoolSize)

	// Create and connect
	val pool = PgPool.pool(vertx, connOps, poolOps)
	Database.pgClient = pool

	// Run a test query to ensure a connection can be made
	val conn = pool.connection.await()
	try {
		conn.query("SELECT 1").execute().await()
	} finally {
		conn.close().await()
	}
}

/**
 * Runs database migrations.
 * Note that this method is runs migrations with JDBC and is therefore blocking.
 * @return The migration result
 * @since 2.0.0
 */
fun dbMigrate(config: AppConfig): MigrateResult {
	// Create FlyWay instance
	val flyway = Flyway.configure().dataSource(
		"jdbc:postgresql://${config.dbHost}:${config.dbPort}/${config.dbName}",
		config.dbAuthUser,
		config.dbAuthPassword
	).locations(Location("twinemedia/db/migration")).load()

	// Run database migrations
	return flyway.migrate()
}

/**
 * Closes the database connection
 * @since 2.0.0
 */
suspend fun dbClose() {
	Database.pgClient?.close()?.await()
}

/**
 * Returns a connection for the database
 * @return A connection for the database
 * @since 1.4.0
 */
suspend fun dbConn(): SqlConnection {
	return Database.client.connection.await()
}
