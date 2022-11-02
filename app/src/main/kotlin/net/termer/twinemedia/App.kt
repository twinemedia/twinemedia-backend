package net.termer.twinemedia

import io.vertx.core.Vertx
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.termer.twinemedia.util.Crypto
import net.termer.twinemedia.util.toJsonObject
import net.termer.twinemedia.verticle.ApiVerticle
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.math.max
import kotlin.system.exitProcess

/**
 * Application entry point
 * @since 2.0.0
 * @author termer
 */
@DelicateCoroutinesApi
class App {
	companion object {
		private val logger: Logger = LoggerFactory.getLogger(App::class.java)
		private val cwdPath = Path.of(System.getProperty("user.dir"))

		/**
		 * Loads the app config (optionally creating a default one and exiting if none exists) and returns it
		 * @param path The path to the config
		 * @param createIfNotExists Whether to create a default config file at the specified path if none exists
		 * @param exitOnCreate Whether to exit when the default config file was created
		 * @return The app config, or null if the config file was not found
		 */
		fun loadConfig(path: Path, createIfNotExists: Boolean, exitOnCreate: Boolean): AppConfig? {
			val config: AppConfig

			logger.info("Loading config...")

			// Create a default config and exit if one doesn't already exist, or return null if createIfNotExists is false
			val file = path.toFile()
			if (!file.exists()) {
				if(createIfNotExists) {
					Files.writeString(
						path,
						AppConfig()
							.toJson()
							.encodePrettily()
					)

					if(exitOnCreate) {
						logger.info("The default configuration file has been written to ${path.toAbsolutePath()}, please edit it before restarting")
						exitProcess(1)
					}
				} else {
					return null
				}
			}

			// Read and parse config
			config = AppConfig.fromJson(
				Files
					.readString(file.toPath())
					.toJsonObject()
			)

			// Resolve absolute paths for path fields
			config.uploadsTmpPath = cwdPath.resolve(config.uploadsTmpPath).pathString
			config.mediaProcessingTmpPath = cwdPath.resolve(config.mediaProcessingTmpPath).pathString
			config.thumbnailsStoragePath = cwdPath.resolve(config.thumbnailsStoragePath).pathString

			return config
		}

		/**
		 * Starts the app
		 * @param config The app config to use
		 */
		private fun start(config: AppConfig) {
			// Create Vert.x instance
			val vertx = Vertx.vertx()

			// Create app context with the resources that were just setup
			val appCtx = AppContext(config)

			// Setup global Crypto instance
			Crypto.instanceInternal = Crypto(vertx, appCtx)

			// Start verticles in coroutine using Vert.x dispatcher
			val runtime = Runtime.getRuntime()
			GlobalScope.launch(vertx.dispatcher()) {
				val appCtxJson = appCtx.toJson()

				// Start API verticle with instances specified by the config "httpServerThreads" property
				val apiVerticleInstances = if(config.httpServerThreads < 1)
					max(runtime.availableProcessors() - config.httpServerThreads, 1)
				else
					config.httpServerThreads
				vertx.deployVerticle(ApiVerticle::class.java, deploymentOptionsOf(
					config = jsonObjectOf("context" to appCtxJson),
					instances = apiVerticleInstances
				)).await()
				logger.info("API is listening on ${config.bindHost}:${config.bindPort} with $apiVerticleInstances thread(s)")
			}

			// Add shutdown hook to close Vert.x
			runtime.addShutdownHook(Thread {
				// Don't run on the Vert.x dispatcher since we're closing Vert.x in this block
				runBlocking {
					vertx.close().await()
				}
			})
		}

		@JvmStatic
		fun main(args: Array<String>) {
			// Create command line options
			val cliOps = Options()
				.addOption("s", "start", false, "Starts the server")
				.addOption("i", "install", false, "Launches an interactive CLI installer")
				.addOption("a", "create-admin", false, "Launches an interactive CLI to create a new admin account")
				.addOption("r", "reset-password", false, "Launches an interactive CLI to reset an account password")
				.addOption(
					"c",
					"config",
					true,
					"Specifies the location of the configuration file to load (if omitted, loads \"config.json\" from the current working directory)"
				)

			// Parse CLI options
			val cli = DefaultParser().parse(cliOps, args)
			val configPath = cwdPath.resolve(Path.of(cli.getOptionValue("config") ?: "config.json"))

			fun getOrMkCfg() = loadConfig(configPath, createIfNotExists = true, exitOnCreate = true)!!

			// Determine what to do based on CLI args
			if (cli.hasOption("start"))
				start(getOrMkCfg())
			else if(cli.hasOption("install"))
				interactiveInstall(configPath)
			else if(cli.hasOption("create-admin"))
				interactiveCreateAdmin(getOrMkCfg())
			else if(cli.hasOption("reset-password"))
				interactiveResetPassword(getOrMkCfg())
			else
				HelpFormatter().printHelp(
					"java -jar " + File(
						App::class.java
							.protectionDomain
							.codeSource
							.location
							.toURI()
					).name, cliOps
				)
		}
	}
}