package net.termer.twinemedia

import io.vertx.core.json.Json
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.termer.twine.utils.StringFilter
import net.termer.twine.utils.files.BlockingReader
import net.termer.twine.utils.files.BlockingWriter
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.db.dataobject.Source
import net.termer.twinemedia.db.dbInit
import net.termer.twinemedia.db.dbMigrate
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.model.SourcesModel
import net.termer.twinemedia.util.createAccount
import net.termer.twinemedia.util.updateAccountPassword
import net.termer.twinemedia.util.validEmail
import java.io.File

// System console
private val cons = System.console()

// Instantiated models
private val accountsModel = AccountsModel()
private val mediaModel = MediaModel()
private val sourcesModel = SourcesModel()

// Notice about Kotlin coroutine calls:
// Coroutine calls are NOT called inside a Vert.x context because blocking calls are done inside of them, intentionally.
// This would both block those threads for normal operation by other parts of the application, and additional throw exceptions because of being blocked.

/**
 * Admin creation prompt
 * @since 1.4.0
 */
@DelicateCoroutinesApi
private fun createAdminPrompt() {
	var email = ""
	while(email.isBlank()) {
		print("Email: ")
		val addr = cons.readLine()

		if(validEmail(addr)) {
			runBlocking	{
				// Check if account with that email already exists
				val emailRes = accountsModel.fetchAccountByEmail(addr)

				if(emailRes.count() > 0)
					println("!!! Account with that email already exists !!!")
				else
					email = addr
			}
		} else {
			println("!!! Invalid email address !!!")
		}
	}

	print("Name: ")
	val name = cons.readLine()

	print("Password (make it strong): ")
	val pass = cons.readPassword().joinToString("")

	println("Creating account...")
	runBlocking	{
		createAccount(name, email, true, arrayOf(), pass, -1)
	}
	println("Created!")
}

/**
 * Runs an interactive install wizard in the console
 * @since 1.4.0
 */
@OptIn(ExperimentalStdlibApi::class)
@DelicateCoroutinesApi
fun interactiveInstall() {
	val cfg = File("configs/twinemedia.json")
	if(cfg.exists()) {
		config = Json.decodeValue(BlockingReader.read(cfg), TwineMediaConfig::class.java)
		if(!config.upload_location.endsWith('/'))
			config.upload_location += '/'
		if(!config.processing_location.endsWith('/'))
			config.processing_location += '/'
	}

	println("Welcome to TwineMedia. You are about to install and configure this module.")
	println("Press enter to continue.")
	var ln = cons.readLine()
	print("Show advanced install options? [y/N]: ")
	val advanced = cons.readLine()?.lowercase()?.startsWith("y") == true

	println("Which Twine domain do you want TwineMedia to run on (can select \"*\" for all)? (${config.domain}): ")
	ln = cons.readLine()

	if(ln != null && ln.trim().isNotBlank())
		config.domain = ln.trim()

	println("What directory do you want uploaded files to be stored? (${config.upload_location}): ")
	ln = cons.readLine()

	if(ln != null && ln.also { ln = ln.trim() }.isNotBlank())
		config.upload_location = if(ln.endsWith('/')) ln else "$ln/"

	// Check if dir exists, if not, ask to create
	val dir = File(config.upload_location)
	if(!dir.exists()) {
		println("Directory \"${config.upload_location}\" doesn't exist, create it? [Y/n]: ")

		if(cons.readLine()?.lowercase()?.startsWith("n") == false)
			dir.mkdirs()
	}

	if(advanced) {
		println("What directory do you want currently processing files to be temporarily stored in? CHOOSE A DIRECTORY THAT CAN SAFELY HAVE ALL OF ITS CONTENTS DELETED. (${config.processing_location}): ")
		ln = cons.readLine()

		if(ln != null && ln.also { ln = ln.trim() }.isNotBlank())
			config.processing_location = if(ln.endsWith('/')) ln else "$ln/"

		// Check if dir exists, if not, ask to create
		val procDir = File(config.processing_location)
		if(!procDir.exists()) {
			println("Directory \"${config.processing_location}\" doesn't exist, create it? [Y/n]: ")

			if(cons.readLine()?.lowercase()?.startsWith("n") == false)
				procDir.mkdirs()
		}
	}

	var size: Long? = null
	while(size == null) {
		println("What maximum upload size do you want (in MB)? (${config.max_upload / 1024 / 1024}): ")
		ln = cons.readLine()

		if(ln != null && ln.trim().isNotBlank()) {
			try {
				size = ln.trim().toLong() * 1024 * 1024
			} catch(e: Exception) {
				println("!!! Value must be an integer !!!")
			}
		} else {
			size = config.max_upload
		}
	}
	config.max_upload = size

	var jwtSecret = StringFilter.generateString(32)

	if(advanced) {
		println("What should the JWT secret key be? ($jwtSecret): ")
		ln = cons.readLine()

		if(ln != null && ln.isNotBlank())
			jwtSecret = ln

		var jwtExpire: Int? = null
		while(jwtExpire == null) {
			println("In how many minutes should JWT tokens expire? (${config.jwt_expire_minutes}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					jwtExpire = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				jwtExpire = config.jwt_expire_minutes
			}
		}
		config.jwt_expire_minutes = jwtExpire
	}

	config.jwt_secret = jwtSecret

	println("What address is the PostgreSQL database running on? (${config.db_address}): ")
	ln = cons.readLine()

	if(ln != null && ln.trim().isNotBlank())
		config.db_address

	var dbPort: Int? = null
	while(dbPort == null) {
		println("What port is the PostgreSQL database running on? (${config.db_port}): ")
		ln = cons.readLine()

		if(ln != null && ln.trim().isNotBlank()) {
			try {
				dbPort = ln.trim().toInt()
			} catch(e: Exception) {
				println("!!! Value must be an integer !!!")
			}
		} else {
			dbPort = config.db_port
		}
	}
	config.db_port = dbPort

	println("What is the name of the database TwineMedia will use? (${config.db_name}): ")
	ln = cons.readLine()

	if(ln != null && ln.trim().isNotBlank())
		config.db_name = ln.trim()

	println("What username are you using to authenticate with the database? (${config.db_user}): ")
	ln = cons.readLine()

	if(ln != null && ln.trim().isNotBlank())
		config.db_user = ln.trim()

	println("What password are you using to authenticate with the database? (<leave blank to use existing/default value>): ")
	ln = cons.readPassword().joinToString("")

	if(ln.isNotBlank())
		config.db_pass = ln

	if(advanced) {
		var dbPool: Int? = null
		while(dbPool == null) {
			println("What should the max database connection pool size be? (${config.db_max_pool_size}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					dbPool = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				dbPool = config.db_max_pool_size
			}
		}
		config.db_max_pool_size = dbPool

		println("Should migrations automatically be run when TwineMedia starts? [${if(config.db_auto_migrate) "Y/n" else "y/N"}]: ")
		ln = cons.readLine().trim()
		if(ln.isNotEmpty())
			config.db_auto_migrate = ln.lowercase().startsWith('y')

		var cryptCount: Int? = null
		while(cryptCount == null) {
			println("How many processors should be used to hash passwords? (${config.crypt_processor_count}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					cryptCount = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				cryptCount = config.crypt_processor_count
			}
		}
		config.crypt_processor_count = cryptCount

		var cryptMem: Int? = null
		while(cryptMem == null) {
			println("How many kilobytes of memory should be used to hash passwords? (${config.crypt_memory_kb}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					cryptMem = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				cryptMem = config.crypt_memory_kb
			}
		}
		config.crypt_memory_kb = cryptMem

		println("What host will the frontend be running on? (${config.frontend_host}): ")
		ln = cons.readLine()

		if(ln != null && ln.trim().isNotBlank())
			config.frontend_host = ln.trim()
	}

	println("Where is ffmpeg located? (${config.ffmpeg_path}): ")
	ln = cons.readLine()

	if(ln != null && ln.trim().isNotBlank())
		config.ffmpeg_path = ln.trim()

	println("Where is ffprobe located? (${config.ffprobe_path}): ")
	ln = cons.readLine()

	if(ln != null && ln.trim().isNotBlank())
		config.ffprobe_path = ln.trim()

	var procCount: Int? = null
	while(procCount == null) {
		println("How many files should TwineMedia be able to process at once? (${config.media_processor_count}): ")
		ln = cons.readLine()

		if(ln != null && ln.trim().isNotBlank()) {
			try {
				procCount = ln.trim().toInt()
			} catch(e: Exception) {
				println("!!! Value must be an integer !!!")
			}
		} else {
			procCount = config.media_processor_count
		}
	}
	config.media_processor_count = procCount

	if(advanced) {
		var authMax: Int? = null
		while(authMax == null) {
			println("How many attempts can a user make to authenticate before they will be throttled? (${config.max_auth_attempts}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					authMax = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				authMax = config.max_auth_attempts
			}
		}
		config.max_auth_attempts = authMax

		var authTimeout: Int? = null
		while(authTimeout == null) {
			println("How many milliseconds will users be throttled after hitting their max auth attempts? (${config.auth_timeout_period}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					authTimeout = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				authTimeout = config.auth_timeout_period
			}
		}
		config.auth_timeout_period = authTimeout

		var minPass: Int? = null
		while(minPass == null) {
			println("What should be the minimum allowed password length? (${config.password_require_min}): ")
			ln = cons.readLine()

			if(ln != null && ln.trim().isNotBlank()) {
				try {
					minPass = ln.trim().toInt()
				} catch(e: Exception) {
					println("!!! Value must be an integer !!!")
				}
			} else {
				minPass = config.password_require_min
			}
		}
		config.password_require_min = minPass

		println("Should passwords require an uppercase letter? [${if(config.password_require_uppercase) "Y/n" else "y/N"}]: ")
		ln = cons.readLine().trim()
		if(ln.isNotEmpty())
			config.password_require_uppercase = ln.lowercase().startsWith("y")

		println("Should passwords require a number? [${if(config.password_require_number) "Y/n" else "y/N"}]: ")
		ln = cons.readLine().trim()
		if(ln.isNotEmpty())
			config.password_require_number = ln.lowercase().startsWith("y")

		println("Should passwords require a special character? [${if(config.password_require_special) "Y/n" else "y/N"}]: ")
		ln = cons.readLine().trim()
		if(ln.isNotEmpty())
			config.password_require_special = ln.lowercase().startsWith("y")
	}

	// Check if config exists, ask for overwrite confirmation if it does
	if(cfg.exists()) {
		println("A configuration file already exists, replace it? [y/N]: ")
		if(cons.readLine().lowercase().startsWith("y")) {
			BlockingWriter.write("configs/twinemedia.json", Json.encodePrettily(config))
		}
	} else {
		BlockingWriter.write("configs/twinemedia.json", Json.encodePrettily(config))
	}

	runBlocking	{
		// Connect to database
		println("Testing database connection...")
		try {
			dbInit()
			println("Database connection successful!")
			if(config.db_auto_migrate) {
				println("Setting up schema...")
				dbMigrate()
			}
			println("Checking for an admin account...")
			val admins = accountsModel.fetchAdminAccounts()
			if(admins.count() > 0) {
				println("An admin account already exists")
			} else {
				println("No admin account exists")
			}

			println("Would you like to create one? [Y/n]: ")
			if(cons.readLine().lowercase().startsWith("y")) {
				createAdminPrompt()
			}
		} catch(e: Exception) {
			System.err.println("Error occurred when attempting to access database, check your database settings. Error:")
			e.printStackTrace()
		}
	}

	println("Installation and configuration complete!")
}

/**
 * Runs an interactive administrator password reset wizard in the console
 * @since 1.4.0
 */
@OptIn(ExperimentalStdlibApi::class)
@DelicateCoroutinesApi
fun interactiveResetAdminPassword() {
	val cfg = File("configs/twinemedia.json")
	if(cfg.exists()) {
		config = Json.decodeValue(BlockingReader.read(cfg), TwineMediaConfig::class.java)
		if(!config.upload_location.endsWith('/'))
			config.upload_location += '/'
		if(!config.processing_location.endsWith('/'))
			config.processing_location += '/'
	} else {
		println("TwineMedia is not configured, please run Twine with the --twinemedia-install option to configure and install everything")
		return
	}

	println("Welcome to TwineMedia. You are about to reset the password of an administrator account.")
	println("Press enter to continue.")
	cons.readLine()

	println("Attempting to connect to the database...")
	dbInit()
	println("Connected!")

	runBlocking	{
		println("Fetching admin accounts...")
		val adminsRes = accountsModel.fetchAdminAccounts()

		if(adminsRes.count() > 0) {
			println("Select the ID of the account you want to reset the password of:")

			var idColWidth = 2
			var nameColWidth = 4
			var emailColWidth = 5

			for(acc in adminsRes) {
				if(acc.id.toString().length > idColWidth)
					idColWidth = acc.id.toString().length
				if(acc.name.length > nameColWidth)
					nameColWidth = acc.name.length
				if(acc.email.length > emailColWidth)
					emailColWidth = acc.email.length
			}

			// Print columns
			print("ID")
			repeat(idColWidth - 2) { print(' ') }
			print(" | Name")
			repeat(nameColWidth - 4) { print(' ') }
			print(" | Email")
			repeat(emailColWidth - 5) { print(' ') }
			println()

			for(acc in adminsRes) {
				val id = acc.id.toString()
				val name = acc.name
				val email = acc.email

				// Print row
				print(id)
				repeat(idColWidth - id.length) { print(' ') }
				print(" | $name")
				repeat(nameColWidth - name.length) { print(' ') }
				print(" | $email")
				repeat(emailColWidth - email.length) { print(' ') }
				println()
			}

			try {
				print("Account ID: ")
				val id = cons.readLine().toInt()

				// Check if account is in list
				var exists = false
				for(acc in adminsRes)
					if(acc.id == id) {
						exists = true
						break
					}

				if(exists) {
					print("New password: ")
					val pass = cons.readPassword().joinToString("")
					print("Confirm password: ")
					val passConf = cons.readPassword().joinToString("")

					// Validation
					when {
						pass.isEmpty() -> println("!!! Passwords cannot be blank !!!")
						pass == passConf -> {
							try {
								println("Updating password...")
								updateAccountPassword(id, pass)
								println("Updated!")
							} catch(e: Exception) {
								System.err.println("Failed to update password because of error:")
								e.printStackTrace()
							}
						}
						else -> println("!!! Passwords do not match !!!")
					}
				} else {
					println("!!! Invalid ID !!!")
				}
			} catch(e: Exception) {
				println("!!! Invalid ID !!!")
			}
		} else {
			println("No admin accounts exist, would you like to create one? [Y/n]: ")
			if(cons.readLine().lowercase().startsWith("y")) {
				createAdminPrompt()
			}
		}
	}
}

/**
 * Runs an interactive media source migration for 1.4.0 -> 1.5.0 migration
 * @since 1.5.0
 */
@OptIn(ExperimentalStdlibApi::class)
@DelicateCoroutinesApi
fun interactiveMediaSourceMigration() {
	println("You are about to start the media source migration process. This will create a new media source based on where your files are already stored, and set it as the default for accounts that have not yet been migrated.")
	println("Press enter to continue.")
	cons.readLine()

	println("Attempting to connect to the database...")
	dbInit()
	println("Connected!")

	runBlocking	{
		// Check for unmigrated users
		val sansSrcAccounts = accountsModel.fetchAccountsWithoutSourceCount()

		if(sansSrcAccounts < 1) {
			println("There are no unmigrated accounts")
		}

		// Fetch the amount of sources there are
		val srcCount = sourcesModel.fetchSourcesCount()

		if(srcCount > 0)
			println("There are $srcCount existing source(s). This means media or accounts have likely been at least partially migrated.")

		val src: Source?

		print("Do you want to create a source based on your old upload location in the TwineMedia configuration file? [Y/n]: ")
		if(!readLine()!!.lowercase().startsWith('n')) {
			// Create source configuration
			val srcCfg = json {obj(
					"directory" to config.upload_location,
					"index_subdirs" to false
			)}

			// Create source
			sourcesModel.createSource("local_directory", "Local Directory Files", srcCfg, -1, true)

			// Fetch source to get its ID
			val srcRes = sourcesModel.fetchAllSources(0, 1, 0)

			if(srcRes.rowCount() < 1) {
				println("Failed to find source after creating it, something is wrong")
				return@runBlocking
			}
			src = srcRes.first()

			println("Source created. Since the source is using the directory defined by \"upload_location\", you should change that value to an empty directory that can be used for temporary uploads.")
		} else if(srcCount < 1) {
			println("There are no sources available, you need to create one to continue.")
			return@runBlocking
		} else {
			// Fetch all sources to choose from
			val srcs = sourcesModel.fetchAllSources(0, 999999, 0)

			println("Current sources:")
			for((i, source) in srcs.withIndex()) {
				println("${i+1}. ${source.name} (${source.type})")
			}
			print("Enter the source number to use: ")
			val id = try { readLine()!!.toInt() } catch(e: NumberFormatException) { 0 }
			if(id < 1 || id > srcs.rowCount()) {
				println("Invalid source number")
				return@runBlocking
			}

			src = srcs.elementAt(id-1)
		}

		// Check for unmigrated count
		val sansSrcMedia = mediaModel.fetchMediaCountBySource(-1)

		if(sansSrcMedia < 1) {
			println("There is no unmigrated media")
		}

		println("Migrating accounts and media to new source...")
		accountsModel.updateAccountDefaultSourceByDefaultSource(-1, src.id)
		mediaModel.updateMediaSourceBySource(-1, src.id)

		println("Migration finished! Remember to ensure the file of \"upload_location\" is an empty directory that can be used for temporarily storing uploading files!")
	}
}