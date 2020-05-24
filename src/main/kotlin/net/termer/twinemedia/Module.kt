package net.termer.twinemedia

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.awaitBlocking
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.termer.twine.ServerManager.vertx
import net.termer.twine.ServerManager.ws
import net.termer.twine.modules.TwineModule
import net.termer.twine.modules.TwineModule.Priority.LOW
import net.termer.twine.utils.FileChecker
import net.termer.twine.utils.Reader
import net.termer.twine.utils.Writer
import net.termer.twinemedia.controller.*
import net.termer.twinemedia.db.dbClose
import net.termer.twinemedia.db.dbInit
import net.termer.twinemedia.jwt.jwtInit
import net.termer.twinemedia.middleware.authMiddleware
import net.termer.twinemedia.middleware.headersMiddleware
import net.termer.twinemedia.model.refreshTags
import net.termer.twinemedia.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import kotlin.math.log

class Module : TwineModule {
    companion object {
        val logger : Logger = LoggerFactory.getLogger(Module::class.java)
        var config = TwineMediaConfig()
        val crypt : Crypt = Crypt()
    }

    override fun preinitialize() {
        logger.info("Setting up uploader routes and middleware...")
        headersMiddleware()
        authMiddleware()
        uploadController()
    }

    override fun initialize() {
        try {
            // Setup config
            logger.info("Setting up filesystem...")
            val cfg = File("configs/twinemedia.json")
            if (cfg.exists()) {
                config = Json.decodeValue(Reader.read(cfg), TwineMediaConfig::class.java)
                if (!config.upload_location.endsWith('/'))
                    config.upload_location += '/'
            } else {
                Writer.write("configs/twinemedia.json", Json.encodePrettily(config))
            }
            val uploadLoc = File(config.upload_location)
            if (!uploadLoc.exists() || !uploadLoc.isDirectory) {
                uploadLoc.mkdirs()
            }

            // Ensure directories exist
            FileChecker.createIfNotPresent(arrayOf(
                    config.upload_location,
                    config.upload_location + "/thumbnails/"
            ))

            // Setup database
            logger.info("Setting up database...")
            dbInit()

            // Setup JWT
            logger.info("Setting up JWT keystore...")
            jwtInit()

            // Setup controllers
            logger.info("Setting up controllers...")
            authController()
            accountController()
            serveController()
            mediaController()
            tagsController()
            accountsController()
            mediaChildController()
            processesController()
            listsController()

            // Allow upload status event bus channels over websocket
            ws().outboundRegex("twinemedia\\..*")

            // Start media processor threads
            logger.info("Starting media processors...")
            repeat(config.media_processor_count.coerceAtLeast(1)) {
                startMediaProcessor()
            }

            runBlocking {
                // Refresh tags
                logger.info("Refreshing tags...")
                refreshTags()

                logger.info("Started!")
            }
        } catch(e : IOException) {
            logger.error("Failed to initialize TwineMedia:")
            e.printStackTrace()
        }
    }
    override fun shutdown() {
        logger.info("Closing database connection...")
        dbClose()
    }

    override fun name() = "TwineMedia"
    override fun priority() = LOW
    override fun twineVersion() = "1.5+"
}