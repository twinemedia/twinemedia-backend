package net.termer.twinemedia

import io.vertx.core.Vertx
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.termer.twinemedia.db.dbInit
import net.termer.twinemedia.db.dbMigrate
//import net.termer.twinemedia.model.AccountsModel
//import net.termer.twinemedia.model.MediaModel
//import net.termer.twinemedia.model.SourcesModel
import net.termer.twinemedia.util.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.system.exitProcess

// System console
private val cons = System.console()

// Instantiated models
// TODO import models
//private val accountsModel = AccountsModel()
//private val mediaModel = MediaModel()
//private val sourcesModel = SourcesModel()

// Notice about Kotlin coroutine calls:
// Coroutine calls are NOT called inside a Vert.x context because blocking calls are done inside them, intentionally.
// This would both block those threads for normal operation by other parts of the application, and additionally throw exceptions because of being blocked.

/**
 * Runs an interactive admin creation wizard in the console
 * @since 2.0.0
 */
@DelicateCoroutinesApi
private fun interactiveCreateAdmin(config: AppConfig) {
//	var email = ""
//	while(email.isBlank()) {
//		print("Email: ")
//		val addr = cons.readLine()
//
//		if(isEmailValid(addr)) {
//			runBlocking	{
//				// Check if account with that email already exists
//				val emailRes = accountsModel.fetchAccountByEmail(addr)
//
//				if(emailRes.count() > 0)
//					println("!!! Account with that email already exists !!!")
//				else
//					email = addr
//			}
//		} else {
//			println("!!! Invalid email address !!!")
//		}
//	}
//
//	print("Name: ")
//	val name = cons.readLine()
//
//	print("Password (make it strong): ")
//	val pass = cons.readPassword().joinToString("")
//
//	println("Creating account...")
//	runBlocking	{
//		createAccount(name, email, true, arrayOf(), pass, -1)
//	}
//	println("Created!")
}

/**
 * Runs an interactive install wizard in the console
 * @since 2.0.0
 */
@DelicateCoroutinesApi
fun interactiveInstall(configPath: Path) {
	val vertx = Vertx.vertx()
	val defaultConfig = AppConfig()

	// Get current working directory's path to use for resolving relative paths
	val cwdPath = Path.of(System.getProperty("user.dir"))

	println("You are about to install and configure ${Constants.APP_NAME}.")
	println("Press enter to continue.")
	cons.readLine()

	// Read config, or use default if it doesn't exist
	val config = if(Files.exists(configPath))
		AppConfig.fromJson(Files.readString(configPath).toJsonObject())
	else
		defaultConfig

	val advanced = cons.promptYesNo("Show advanced install options?")

	config.apiAllowOrigin = cons.promptLine((if(advanced)
		"Which HTTP origin will you allow API requests from?"
	else
		"Which domain will your ${Constants.APP_NAME} frontend be served on")+" (can use \"*\" for all)?", config.apiAllowOrigin)

	config.uploadsTmpPath = cwdPath.resolve(
		Path.of(cons.promptLine("In which directory do you want uploaded files to be temporarily stored?", config.uploadsTmpPath))
	).pathString

	// Check if dir exists and ask to create it if not
	val uploadsTmpDir = File(config.uploadsTmpPath)
	if(!uploadsTmpDir.exists()) {
		println("Directory \"${config.uploadsTmpPath}\" doesn't exist, create it? [Y/n]: ")

		if(cons.promptYesNo("Directory \"${config.uploadsTmpPath}\" doesn't exist, create it?", true))
			uploadsTmpDir.mkdirs()
	}

	if(advanced) {
		config.mediaProcessingTmpPath = cwdPath.resolve(
			Path.of(cons.promptLine("In which directory do you want currently processing files to be temporarily stored? CHOOSE A DIRECTORY THAT CAN SAFELY HAVE ALL OF ITS CONTENTS DELETED!", config.mediaProcessingTmpPath))
		).pathString

		// Check if dir exists, if not, ask to create
		val procTmpDir = File(config.mediaProcessingTmpPath)
		if(!procTmpDir.exists()) {
			if(cons.promptYesNo("Directory \"${config.mediaProcessingTmpPath}\" doesn't exist, create it?", true))
				procTmpDir.mkdirs()
		}
	}

	config.maxUploadSize = cons.promptNumber("What maximum upload size do you want (in MB)?", config.maxUploadSize / 1024 / 1024) * 1024 * 1024

	// Generate new JWT secret if the default hasn't been changed
	var jwtSecret = if(config.jwtSecret == defaultConfig.jwtSecret)
		genStrOf(ALPHANUMERIC_CHARS, 32)
	else
		config.jwtSecret

	// If advanced options are enabled, allow a custom JWT key
	if(advanced) {
		jwtSecret = cons.promptLine("What should the JWT secret key be?", jwtSecret)

		config.jwtExpireMinutes = cons.promptNumber("In how many minutes should JWT tokens expire?", config.jwtExpireMinutes)
	}

	config.jwtSecret = jwtSecret

	config.dbHost = cons.promptLine("On which host is the PostgreSQL database running?", config.dbHost)
	config.dbPort = cons.promptNumber("On which port is the PostgreSQL database running?", config.dbPort)
	config.dbName = cons.promptLine("What is the name of the database ${Constants.APP_NAME} will be using?", config.dbName)
	config.dbAuthUser = cons.promptLine("What is the username that ${Constants.APP_NAME} will use to authenticate with the database?", config.dbAuthUser)
	config.dbAuthPassword = cons.promptLine(
		message = "What is the password that ${Constants.APP_NAME} will use to authenticate with the database? (<leave blank to use existing/default value>)",
		default = "",
		trim = false,
		promptUntilNonEmpty = false,
		disableEcho = true
	).nullIfEmpty() ?: config.dbAuthPassword

	if(advanced) {
		config.dbMaxPoolSize = cons.promptNumber("What should the maximum number of open connections to keep in the database connection pool be?", config.dbMaxPoolSize)
		config.dbAutoMigrate = cons.promptYesNo("Should database migrations automatically be run when ${Constants.APP_NAME} starts?", config.dbAutoMigrate)

		config.passwordHashThreadCount = cons.promptNumber("How many threads should be used to hash passwords?", config.passwordHashThreadCount)
		config.passwordHashMemoryKb = cons.promptNumber("How many kilobytes of memory should be used to hash passwords?", config.passwordHashMemoryKb)

		config.httpServerThreads = cons.promptNumber("How many threads should be used for serving HTTP requests?", config.httpServerThreads)
	}

	config.ffmpegPath = cons.promptLine("At which path is your FFmpeg binary located?", config.ffmpegPath)
	config.ffprobePath = cons.promptLine("At which path is your FFprobe binary located?", config.ffprobePath)

	config.mediaProcessorQueues = cons.promptNumber("How many media files should ${Constants.APP_NAME} be able to process to different formats/resolutions at once?", config.mediaProcessorQueues)

	if(advanced) {
		config.authMaxFailedAttempts = cons.promptNumber("How many failed authentication attempts is an IP address allowed make before being subject to a timeout?", config.authMaxFailedAttempts)
		config.authTimeoutSeconds = cons.promptNumber("For how many seconds should the timeout last?", config.authTimeoutSeconds)

		config.passwordRequireLength = cons.promptNumber("What should be the minimum allowed account password length?", config.passwordRequireLength)
		config.passwordRequireUppercase = cons.promptYesNo("Should account passwords require an uppercase letter?", config.passwordRequireUppercase)
		config.passwordRequireNumber = cons.promptYesNo("Should account passwords require a number?", config.passwordRequireNumber)
		config.passwordRequireSpecial = cons.promptYesNo("Should passwords require a special character?", config.passwordRequireSpecial)
	}

	// Check if config exists, ask for overwrite confirmation if it does
	if(Files.exists(configPath) && !cons.promptYesNo("A configuration file already exists at ${configPath.pathString}, do you want to overwrite it?")) {
		println("Configuration changes not saved")
		exitProcess(0)
	}

	Files.writeString(configPath, config.toJson().encodePrettily())
	println("Wrote configuration to ${configPath.pathString}")

	runBlocking	{
		// Connect to database
		println("Testing database connection...")
		try {
			dbInit(vertx, config)
			println("Database connection successful!")
			if(config.dbAutoMigrate) {
				println("Setting up schema...")
				dbMigrate(config)
			}
			// TODO Add back when accounts model is imported
//			println("Checking for an admin account...")
//			val admins = accountsModel.fetchAdminAccounts()
//			if(admins.count() > 0) {
//				println("An admin account already exists")
//			} else {
//				println("No admin account exists")
//			}
//
//			if(cons.promptYesNo("Would you like to create one?", true))
//				interactiveCreateAdmin(config)
		} catch(e: Exception) {
			System.err.println("Error occurred when attempting to access database, check your database settings. Error:")
			e.printStackTrace()
		}
	}

	println("Installation and configuration complete!")
}

///**
// * Runs an interactive account password reset wizard in the console
// * @since 2.0.0
// */
//@DelicateCoroutinesApi
//fun interactiveResetPassword(config: AppConfig) {
//	println("You are about to reset an account's password.")
//	println("Press enter to continue.")
//	cons.readLine()
//
//	println("Attempting to connect to the database...")
//	dbInit()
//	println("Connected!")
//
//	runBlocking	{
//		// TODO Instead of fetching accounts, have the user enter an account's email
//		println("Fetching admin accounts...")
//		val adminsRes = accountsModel.fetchAdminAccounts()
//
//		if(adminsRes.count() > 0) {
//			println("Select the ID of the account you want to reset the password of:")
//
//			var idColWidth = 2
//			var nameColWidth = 4
//			var emailColWidth = 5
//
//			for(acc in adminsRes) {
//				if(acc.id.toString().length > idColWidth)
//					idColWidth = acc.id.toString().length
//				if(acc.name.length > nameColWidth)
//					nameColWidth = acc.name.length
//				if(acc.email.length > emailColWidth)
//					emailColWidth = acc.email.length
//			}
//
//			// Print columns
//			print("ID")
//			repeat(idColWidth - 2) { print(' ') }
//			print(" | Name")
//			repeat(nameColWidth - 4) { print(' ') }
//			print(" | Email")
//			repeat(emailColWidth - 5) { print(' ') }
//			println()
//
//			for(acc in adminsRes) {
//				val id = acc.id.toString()
//				val name = acc.name
//				val email = acc.email
//
//				// Print row
//				print(id)
//				// TODO Instead of using repeat, create genStrOf(char, len) util in MiscUtils
//				repeat(idColWidth - id.length) { print(' ') }
//				print(" | $name")
//				repeat(nameColWidth - name.length) { print(' ') }
//				print(" | $email")
//				repeat(emailColWidth - email.length) { print(' ') }
//				println()
//			}
//
//			try {
//				print("Account ID: ")
//				val id = cons.readLine().toInt()
//
//				// Check if account is in list
//				var exists = false
//				for(acc in adminsRes)
//					if(acc.id == id) {
//						exists = true
//						break
//					}
//
//				if(exists) {
//					print("New password: ")
//					val pass = cons.readPassword().joinToString("")
//					print("Confirm password: ")
//					val passConf = cons.readPassword().joinToString("")
//
//					// Validation
//					when {
//						pass.isEmpty() -> println("!!! Passwords cannot be blank !!!")
//						pass == passConf -> {
//							try {
//								println("Updating password...")
//								updateAccountPassword(id, pass)
//								println("Updated!")
//							} catch(e: Exception) {
//								System.err.println("Failed to update password because of error:")
//								e.printStackTrace()
//							}
//						}
//						else -> println("!!! Passwords do not match !!!")
//					}
//				} else {
//					println("!!! Invalid ID !!!")
//				}
//			} catch(e: Exception) {
//				println("!!! Invalid ID !!!")
//			}
//		} else {
//			println("No admin accounts exist, would you like to create one? [Y/n]: ")
//			if(cons.readLine().lowercase().startsWith("y")) {
//				createAdminPrompt()
//			}
//		}
//	}
//}