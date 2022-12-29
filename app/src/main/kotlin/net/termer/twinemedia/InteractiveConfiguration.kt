package net.termer.twinemedia

import io.vertx.core.Vertx
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.util.*
import net.termer.twinemedia.util.db.dbInit
import net.termer.twinemedia.util.db.dbMigrate
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.system.exitProcess

// System console
private val cons = System.console()

// Instantiated models
private val accountsModel = AccountsModel.INSTANCE

// Notice about Kotlin coroutine calls:
// Coroutine calls are NOT called inside a Vert.x context because blocking calls are done inside them, intentionally.
// This would both block those threads for normal operation by other parts of the application, and additionally throw exceptions because of being blocked.

/**
 * Throws [IllegalStateException] if there is no console available
 */
private fun checkConsole() {
	if(cons == null)
		throw IllegalStateException("Interactive configuration cannot be performed if there is no console available")
}

/**
 * Runs an interactive admin creation wizard in the console
 * @param config The config to use
 * @param vertx The Vert.x instance to use (defaults to [Vertx.vertx])
 * @param shutDown Whether to shut down after finishing (defaults to true)
 * @since 2.0.0
 */
@DelicateCoroutinesApi
fun interactiveCreateAdmin(config: AppConfig, vertx: Vertx = Vertx.vertx(), shutDown: Boolean = true) {
	try {
		checkConsole()

		var email = ""
		while (email.isBlank()) {
			print("Email: ")
			val addr = cons.readLine()

			if (isEmailValid(addr)) {
				runBlocking {
					// Check if account with that email already exists
					val emailAccCount = accountsModel.count(AccountsModel.Filters(whereEmailIs = some(addr)))

					if (emailAccCount > 0)
						println("Account with that email already exists")
					else
						email = addr
				}
			} else {
				println("Invalid email address")
			}
		}

		var name = ""
		while (name.isBlank()) {
			print("Name: ")
			name = cons.readLine()
		}

		var pass = ""
		while (pass.isBlank()) {
			print("Password: ")
			val tmpPass = cons.readPassword().joinToString("")

			print("Confirm password: ")
			val passConfirm = cons.readPassword().joinToString("")

			// Validate password
			if (tmpPass != passConfirm) {
				println("Passwords do not match")
				continue
			}
			val valRes = validatePassword(tmpPass, config)
			if (valRes != null) {
				println("Password validation error: " + valRes.name)
				continue
			}

			// Validation succeeded
			pass = tmpPass
		}

		println("Creating account...")
		runBlocking {
			createAccount(
				name = name,
				email = email,
				isAdmin = true,
				password = pass,
				crypto = Crypto(vertx, AppContext(config))
			)
		}
		println("Created!")
	} finally {
		if(shutDown)
			exitProcess(0)
	}
}

/**
 * Runs an interactive install wizard in the console
 * @param configPath The path to the config to read/write
 * @param shutDown Whether to shut down after finishing (defaults to true)
 * @since 2.0.0
 */
@DelicateCoroutinesApi
fun interactiveInstall(configPath: Path, shutDown: Boolean = true) {
	try {
		checkConsole()

		val vertx = Vertx.vertx()
		val defaultConfig = AppConfig()

		// Get current working directory's path to use for resolving relative paths
		val cwdPath = Path.of(System.getProperty("user.dir"))

		println("You are about to install and configure ${Constants.APP_NAME}.")
		println("Press enter to continue.")
		cons.readLine()

		// Read config, or use default if it doesn't exist
		val config = if (Files.exists(configPath))
			AppConfig.fromJson(Files.readString(configPath).toJsonObject())
		else
			defaultConfig

		val advanced = cons.promptYesNo("Show advanced install options?")

		config.apiAllowOrigin = cons.promptLine(
			(if (advanced)
				"Which HTTP origin will you allow API requests from?"
			else
				"Which domain will your ${Constants.APP_NAME} frontend be served on") + " (can use \"*\" for all)?",
			config.apiAllowOrigin
		)

		config.uploadsTmpPath = cwdPath.resolve(
			Path.of(
				cons.promptLine(
					"In which directory do you want uploaded files to be temporarily stored?",
					config.uploadsTmpPath
				)
			)
		).pathString

		// Check if dir exists and ask to create it if not
		val uploadsTmpDir = File(config.uploadsTmpPath)
		if (!uploadsTmpDir.exists()) {
			if (cons.promptYesNo("Directory \"${config.uploadsTmpPath}\" doesn't exist, create it?", true))
				uploadsTmpDir.mkdirs()
		}

		// Media process temporary folder path
		if (advanced) {
			config.mediaProcessingTmpPath = cwdPath.resolve(
				Path.of(
					cons.promptLine(
						"In which directory do you want currently processing files to be temporarily stored? CHOOSE A DIRECTORY THAT CAN SAFELY HAVE ALL OF ITS CONTENTS DELETED!",
						config.mediaProcessingTmpPath
					)
				)
			).pathString
		}

		// Check if dir exists, if not, ask to create
		val procTmpDir = File(config.mediaProcessingTmpPath)
		if (!procTmpDir.exists()) {
			if (cons.promptYesNo("Directory \"${config.mediaProcessingTmpPath}\" doesn't exist, create it?", true))
				procTmpDir.mkdirs()
		}

		config.maxUploadSize = cons.promptNumber(
			"What maximum upload size do you want (in MB)?",
			config.maxUploadSize / 1024 / 1024
		) * 1024 * 1024

		// Generate new JWT secret if the default hasn't been changed
		var jwtSecret = if (config.jwtSecret == defaultConfig.jwtSecret)
			genStrOf(LETTERS_NUMBERS_UNDERSCORES_DASHES_CHARS, 32)
		else
			config.jwtSecret

		// If advanced options are enabled, allow a custom JWT key
		if (advanced) {
			jwtSecret = cons.promptLine("What should the JWT secret key be?", jwtSecret)

			config.jwtExpireMinutes =
				cons.promptNumber("In how many minutes should JWT tokens expire?", config.jwtExpireMinutes)
		}

		config.jwtSecret = jwtSecret

		config.dbHost = cons.promptLine("On which host is the PostgreSQL database running?", config.dbHost)
		config.dbPort = cons.promptNumber("On which port is the PostgreSQL database running?", config.dbPort)
		config.dbName =
			cons.promptLine("What is the name of the database ${Constants.APP_NAME} will be using?", config.dbName)
		config.dbAuthUser = cons.promptLine(
			"What is the username that ${Constants.APP_NAME} will use to authenticate with the database?",
			config.dbAuthUser
		)
		config.dbAuthPassword = cons.promptLine(
			message = "What is the password that ${Constants.APP_NAME} will use to authenticate with the database? (<leave blank to use existing/default value>)",
			default = "",
			trim = false,
			promptUntilNonEmpty = false,
			disableEcho = true
		).nullIfEmpty() ?: config.dbAuthPassword

		if (advanced) {
			config.dbMaxPoolSize = cons.promptNumber(
				"What should the maximum number of open connections to keep in the database connection pool be?",
				config.dbMaxPoolSize
			)
			config.dbAutoMigrate = cons.promptYesNo(
				"Should database migrations automatically be run when ${Constants.APP_NAME} starts?",
				config.dbAutoMigrate
			)

			config.passwordHashThreadCount =
				cons.promptNumber("How many threads should be used to hash passwords?", config.passwordHashThreadCount)
			config.passwordHashMemoryKib = cons.promptNumber(
				"How many kibibytes (1024 bytes) of memory should be used to hash passwords?",
				config.passwordHashMemoryKib
			)

			config.httpServerThreads = cons.promptNumber(
				"How many threads should be used for serving HTTP requests?",
				config.httpServerThreads
			)
		}

		config.ffmpegPath = cons.promptLine("At which path is your FFmpeg binary located?", config.ffmpegPath)
		config.ffprobePath = cons.promptLine("At which path is your FFprobe binary located?", config.ffprobePath)

		config.mediaProcessorQueues = cons.promptNumber(
			"How many media files should ${Constants.APP_NAME} be able to process to different formats/resolutions at once?",
			config.mediaProcessorQueues
		)

		if (advanced) {
			config.authMaxFailedAttempts = cons.promptNumber(
				"How many failed authentication attempts is an IP address allowed make before being subject to a timeout?",
				config.authMaxFailedAttempts
			)
			config.authTimeoutSeconds =
				cons.promptNumber("For how many seconds should the timeout last?", config.authTimeoutSeconds)

			config.passwordRequireLength = cons.promptNumber(
				"What should be the minimum allowed account password length?",
				config.passwordRequireLength
			)
			config.passwordRequireUppercase = cons.promptYesNo(
				"Should account passwords require an uppercase letter?",
				config.passwordRequireUppercase
			)
			config.passwordRequireNumber =
				cons.promptYesNo("Should account passwords require a number?", config.passwordRequireNumber)
			config.passwordRequireSpecial =
				cons.promptYesNo("Should passwords require a special character?", config.passwordRequireSpecial)
		}

		// Check if config exists, ask for overwrite confirmation if it does
		if (Files.exists(configPath) && !cons.promptYesNo("A configuration file already exists at ${configPath.pathString}, do you want to overwrite it?")) {
			println("Configuration changes not saved")
			exitProcess(0)
		}

		Files.writeString(configPath, config.toJson().encodePrettily())
		println("Wrote configuration to ${configPath.pathString}")

		runBlocking {
			// Connect to database
			println("Testing database connection...")
			try {
				dbInit(vertx, config)
				println("Database connection successful!")

				if (config.dbAutoMigrate) {
					println("Setting up schema...")
					dbMigrate(config)
				}

				println("Checking for an admin account...")
				val adminsCount = accountsModel.count(AccountsModel.Filters(whereAdminStatusIs = some(true)))

				if (adminsCount > 0)
					println("An admin account already exists")
				else
					println("No admin account exists")

				if (cons.promptYesNo("Would you like to create one?", true))
					interactiveCreateAdmin(config, vertx)
			} catch (e: Exception) {
				System.err.println("Error occurred when attempting to access database, check your database settings. Error:")
				e.printStackTrace()
			}
		}

		println("Installation and configuration complete!")
	} finally {
		if(shutDown)
			exitProcess(0)
	}
}

/**
 * Runs an interactive account password reset wizard in the console
 * @param config The config to use
 * @param shutDown Whether to shut down after finishing (defaults to true)
 * @since 2.0.0
 */
@DelicateCoroutinesApi
fun interactiveResetPassword(config: AppConfig, shutDown: Boolean = true) {
	try {
		checkConsole()

		val vertx = Vertx.vertx()

		println("You are about to reset an account's password.")
		println("Press enter to continue.")
		cons.readLine()

		println("Attempting to connect to the database...")
		dbInit(vertx, config)
		println("Connected!")

		runBlocking {
			val email = cons.readLine("Account email: ")

			println("Fetching account info...")
			val account = accountsModel.fetchOneRow(AccountsModel.Filters(whereEmailIs = some(email)))

			// Check if account exists
			if (account == null) {
				println("No account with that email was found")
				return@runBlocking
			}

			// Get and confirm password
			print("New password: ")
			val pass = cons.readPassword().joinToString("")
			print("Confirm password: ")
			val passConfirm = cons.readPassword().joinToString("")

			// Validate password
			if (pass != passConfirm) {
				println("Passwords do not match")
				return@runBlocking
			}
			val valRes = validatePassword(pass, config)
			if (valRes != null) {
				println("Password validation error: " + valRes.name)
				return@runBlocking
			}

			// Update password
			try {
				println("Updating password...")
				updateAccountPassword(account.internalId, pass, crypto = Crypto(vertx, AppContext(config)))
				println("Updated!")
			} catch (e: Exception) {
				System.err.println("Failed to update password due to error:")
				e.printStackTrace()
			}
		}
	} finally {
		if(shutDown)
			exitProcess(0)
	}
}