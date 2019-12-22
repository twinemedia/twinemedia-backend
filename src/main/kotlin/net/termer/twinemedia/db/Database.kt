package net.termer.twinemedia.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLClient
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config

/**
 * Object containing global database fields
 * @since 1.0
 */
object Database {
    var client: SQLClient? = null
}

/**
 * Initializes the database connection
 * @since 1.0
 */
fun dbInit() {
    val cfg = HikariConfig()

    // Set properties
    cfg.jdbcUrl = "jdbc:postgresql://${config.db_address}:${config.db_port}/${config.db_name}"
    cfg.username = config.db_user
    cfg.password = config.db_pass
    cfg.maximumPoolSize = config.db_max_pool_size

    // Create and connect
    Database.client = JDBCClient.create(vertx(), HikariDataSource(cfg))
}

/**
 * Closes the database connection
 * @since 1.0
 */
fun dbClose() {
    Database.client?.close()
}