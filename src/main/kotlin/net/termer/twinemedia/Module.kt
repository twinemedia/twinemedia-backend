package net.termer.twinemedia

import io.vertx.core.json.Json
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.runBlocking
import net.termer.twine.Events
import net.termer.twine.ServerManager.*
import net.termer.twine.Twine
import net.termer.twine.Twine.serverArgs
import net.termer.twine.modules.TwineModule
import net.termer.twine.modules.TwineModule.Priority.LOW
import net.termer.twine.utils.files.BlockingFileChecker
import net.termer.twine.utils.files.BlockingReader
import net.termer.twine.utils.files.BlockingWriter
import net.termer.twinemedia.controller.*
import net.termer.twinemedia.db.dbClose
import net.termer.twinemedia.db.dbInit
import net.termer.twinemedia.db.dbMigrate
import net.termer.twinemedia.db.startTagsViewRefresher
import net.termer.twinemedia.jwt.jwtInit
import net.termer.twinemedia.middleware.authMiddleware
import net.termer.twinemedia.middleware.headersMiddleware
import net.termer.twinemedia.model.TagsModel
import net.termer.twinemedia.sockjs.SockJSManager
import net.termer.twinemedia.source.MediaSourceManager
import net.termer.twinemedia.source.impl.fs.LocalDirectoryMediaSource
import net.termer.twinemedia.source.impl.s3.S3BucketMediaSource
import net.termer.twinemedia.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

class Module : TwineModule {
	// Whether this is a special run, e.g. whether special command line arguments are supplied
	private val specialRun = serverArgs().option("twinemedia-install") || serverArgs().option("twinemedia-reset-admin")

	companion object {
		val logger: Logger = LoggerFactory.getLogger(Module::class.java)
		var config = TwineMediaConfig()
		val crypt: Crypt = Crypt()
		val sockJSManager = SockJSManager()
		val mediaSourceManager = MediaSourceManager()
	}

	/**
	 * Configures the module by loading the config file from disk
	 * @since 1.1.0
	 */
	fun configure() {
		// Setup config
		logger.info("Loading config...")
		val cfg = File("configs/twinemedia.json")
		if(cfg.exists()) {
			config = Json.decodeValue(BlockingReader.read(cfg), TwineMediaConfig::class.java)
			if(!config.upload_location.endsWith('/'))
				config.upload_location += '/'
			if(!config.processing_location.endsWith('/'))
				config.processing_location += '/'
		} else {
			BlockingWriter.write("configs/twinemedia.json", Json.encodePrettily(config))
		}
	}

	override fun preinitialize() {
		if(!specialRun) {
			configure()

			logger.info("Setting up uploader routes and middleware...")
			headersMiddleware()
			authMiddleware()
			uploadController()
		}
	}

	override fun initialize() {
		// Check if this is a special run
		if(specialRun) {
			if(serverArgs().option("twinemedia-install")) {
				interactiveInstall()
			} else if(serverArgs().option("twinemedia-reset-admin")) {
				interactiveResetAdminPassword()
			}

			println("Waiting for Twine to start...")

			// Register server start event to shut it down immediately
			Events.on(Events.Type.SERVER_START) {
				logger.info("Shutting down Twine since TwineMedia configuration is finished...")
				Twine.shutdown()
			}
		} else {
			try {
				logger.info("Setting up filesystem...")
				val uploadLoc = File(config.upload_location)
				if(!uploadLoc.exists() || !uploadLoc.isDirectory) {
					uploadLoc.mkdirs()
				}

				// Ensure directories exist
				BlockingFileChecker.createIfNotPresent(arrayOf(
						config.upload_location,
						config.upload_location+"/thumbnails/",
						config.processing_location
				))

				// Setup database
				logger.info("Setting up database...")
				dbInit()
				runBlocking(vertx().dispatcher()) {
					startTagsViewRefresher()
				}

				// Run migration if db_auto_migrate is true, or if --twinemedia-migrate specified
				if(config.db_auto_migrate || serverArgs().option("twinemedia-migrate")) {
					logger.info("Running database migrations...")
					dbMigrate()
				}

				// Setup JWT
				logger.info("Setting up JWT keystore...")
				jwtInit()

				// Setup SockJS
				sockJSManager.initialize()

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
				apiKeysController()
				infoController()
				notFoundController()

				// Register media sources
				mediaSourceManager
						.registerSource("local_directory", "Local Directory", "Store and index files in a local directory", LocalDirectoryMediaSource::class.java)
						.registerSource("s3_bucket", "S3 Bucket", "Source and index files in an S3 (or S3-compatible) bucket", S3BucketMediaSource::class.java)
						.initialize()
				logger.info("Available media sources:")
				mediaSourceManager.availableSources().forEach { logger.info(" - "+it.name) }

				// Allow upload status event bus channels over websocket
				ws().outboundRegex("twinemedia\\..*")

				// Delete temp files
				logger.info("Deleting temp files...")
				runBlocking(vertx().dispatcher()) {
					deleteFilesInProcessingDirectory()
				}

				// Start media processor threads
				logger.info("Starting media processors...")
				repeat(config.media_processor_count.coerceAtLeast(1)) {
					startMediaProcessor()
				}

				runBlocking(vertx().dispatcher()) {
					val tagsModel = TagsModel()

					// Refresh tags
					logger.info("Refreshing tags...")
					tagsModel.refreshTags()

					logger.info("Started!")
				}

				// Register server config reload hook
				Events.on(Events.Type.CONFIG_RELOAD) {
					try {
						configure()
					} catch(e: IOException) {
						logger.error("Failed to load TwineMedia config:")
						e.printStackTrace()
					}
				}
			} catch(e: IOException) {
				logger.error("Failed to initialize TwineMedia:")
				e.printStackTrace()
			}
		}
	}

	override fun shutdown() {
		if(!specialRun) {
			logger.info("Closing database connection...")
			dbClose()
		}
	}

	override fun name() = "TwineMedia"
	override fun priority() = LOW
	override fun twineVersion() = "2.0+"
}