package net.termer.twinemedia

import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import kotlinx.coroutines.runBlocking
import net.termer.twine.Events
import net.termer.twine.ServerManager.ws
import net.termer.twine.Twine
import net.termer.twine.Twine.serverArgs
import net.termer.twine.modules.TwineModule
import net.termer.twine.modules.TwineModule.Priority.LOW
import net.termer.twine.utils.FileChecker
import net.termer.twine.utils.Reader
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twine.utils.Writer
import net.termer.twinemedia.controller.*
import net.termer.twinemedia.db.dbClose
import net.termer.twinemedia.db.dbInit
import net.termer.twinemedia.db.dbMigrate
import net.termer.twinemedia.jwt.jwtInit
import net.termer.twinemedia.middleware.authMiddleware
import net.termer.twinemedia.middleware.headersMiddleware
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.model.TagsModel
import net.termer.twinemedia.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import kotlin.Exception

class Module: TwineModule {
    // Instantiated models
    private val accountsModel = AccountsModel()

    // Whether this is a special run, e.g. whether special command line arguments are supplied
    val specialRun = serverArgs().option("twinemedia-install") || serverArgs().option("twinemedia-reset-admin")

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Module::class.java)
        var config = TwineMediaConfig()
        val crypt: Crypt = Crypt()
    }

    override fun preinitialize() {
        if(!specialRun) {
            // Setup config
            logger.info("Loading config...")
            val cfg = File("configs/twinemedia.json")
            if (cfg.exists()) {
                config = Json.decodeValue(Reader.read(cfg), TwineMediaConfig::class.java)
                if (!config.upload_location.endsWith('/'))
                    config.upload_location += '/'
            } else {
                Writer.write("configs/twinemedia.json", Json.encodePrettily(config))
            }

            logger.info("Setting up uploader routes and middleware...")
            headersMiddleware()
            authMiddleware()
            uploadController()
        }
    }

    override fun initialize() {
        // System console
        val cons = System.console()

        // Local function to create an admin account
        fun createAdminPrompt() {
            var email = ""
            while(email.isBlank()) {
                print("Email: ")
                val addr = cons.readLine()

                if(validEmail(addr)) {
                    runBlocking {
                        // Check if account with that email already exists
                        val emailRes = accountsModel.fetchAccountByEmail(addr)

                        if (emailRes != null && emailRes.rows!!.isNotEmpty())
                            println("Account with that email already exists")
                        else
                            email = addr
                    }
                } else {
                    println("Invalid email address")
                }
            }

            print("Name: ")
            val name = cons.readLine()

            print("Password: ")
            val pass = cons.readPassword().joinToString("")

            println("Creating account...")
            runBlocking {
                createAccount(name, email, true, JsonArray(), pass)
            }
            println("Created!")
        }

        // Check if this is a special run
        if(specialRun) {
            if(serverArgs().option("twinemedia-install")) {
                val cfg = File("configs/twinemedia.json")
                if (cfg.exists()) {
                    config = Json.decodeValue(Reader.read(cfg), TwineMediaConfig::class.java)
                    if (!config.upload_location.endsWith('/'))
                        config.upload_location += '/'
                }

                println("Welcome to TwineMedia. You are about to install and configure this module.")
                println("Press enter to continue.")
                var ln = cons.readLine()
                print("Show advanced install options? [y/N]: ")
                val advanced = cons.readLine()?.toLowerCase()?.startsWith("y") == true

                println("Which domain do you want TwineMedia to run on (can select \"*\" for all)? (${config.domain}): ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.domain = ln.trim()

                println("What directory do you want uploaded files to be stored? (${config.upload_location}): ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.upload_location = ln.trim()

                // Check if dir exists, if not, ask to create
                val dir = File(config.upload_location)
                if (!dir.exists()) {
                    println("Directory \"${config.upload_location}\" doesn't exist, create it? [Y/n]: ")

                    if (cons.readLine()?.toLowerCase()?.startsWith("n") == false)
                        dir.mkdirs()
                }

                var size: Int? = null
                while (size == null) {
                    println("What maximum upload size do you want (in MB)? (${config.max_upload / 1024 / 1024}): ")
                    ln = cons.readLine()

                    if (ln != null && ln.trim().isNotBlank()) {
                        try {
                            size = ln.trim().toInt() * 1024 * 1024
                        } catch (e: Exception) {
                            println("Value must be an integer")
                        }
                    } else {
                        size = config.max_upload
                    }
                }
                config.max_upload = size

                var jwtSecret = generateString(32)

                if (advanced) {
                    println("What should the JWT secret key be? ($jwtSecret): ")
                    ln = cons.readLine()

                    if (ln != null && ln.isNotBlank())
                        jwtSecret = ln

                    var jwtExpire: Int? = null
                    while (jwtExpire == null) {
                        println("In how many minutes should JWT tokens expire? (${config.jwt_expire_minutes}): ")
                        ln = cons.readLine()

                        if (ln != null && ln.trim().isNotBlank()) {
                            try {
                                jwtExpire = ln.trim().toInt()
                            } catch (e: Exception) {
                                println("Value must be an integer")
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

                if (ln != null && ln.trim().isNotBlank())
                    config.db_address

                var dbPort: Int? = null
                while (dbPort == null) {
                    println("What port is the PostgreSQL database running on? (${config.db_port}): ")
                    ln = cons.readLine()

                    if (ln != null && ln.trim().isNotBlank()) {
                        try {
                            dbPort = ln.trim().toInt()
                        } catch (e: Exception) {
                            println("Value must be an integer")
                        }
                    } else {
                        dbPort = config.db_port
                    }
                }
                config.db_port = dbPort

                println("What is the name of the database TwineMedia will use? (${config.db_name}): ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.db_name = ln.trim()

                println("What username are you using to authenticate with the database? (${config.db_user}): ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.db_user = ln.trim()

                println("What password are you using to authenticate with the database? (<leave blank to use existing/default value>): ")
                ln = cons.readPassword().joinToString("")

                if (ln.isNotBlank())
                    config.db_pass = ln

                if (advanced) {
                    var dbPool: Int? = null
                    while (dbPool == null) {
                        println("What should the max database connection pool size be? (${config.db_max_pool_size}): ")
                        ln = cons.readLine()

                        if (ln != null && ln.trim().isNotBlank()) {
                            try {
                                dbPool = ln.trim().toInt()
                            } catch (e: Exception) {
                                println("Value must be an integer")
                            }
                        } else {
                            dbPool = config.db_max_pool_size
                        }
                    }
                    config.db_max_pool_size = dbPool

                    println("Should migrations automatically be run when TwineMedia starts? [Y/n]: ")
                    config.db_auto_migrate = cons.readLine()?.startsWith("n") == false

                    var cryptCount: Int? = null
                    while (cryptCount == null) {
                        println("How many processors should be used to hash passwords? (${config.crypt_processor_count}): ")
                        ln = cons.readLine()

                        if (ln != null && ln.trim().isNotBlank()) {
                            try {
                                cryptCount = ln.trim().toInt()
                            } catch (e: Exception) {
                                println("Value must be an integer")
                            }
                        } else {
                            cryptCount = config.crypt_processor_count
                        }
                    }
                    config.crypt_processor_count = cryptCount

                    var cryptMem: Int? = null
                    while (cryptMem == null) {
                        println("How many kilobytes of memory should be used to hash passwords? (${config.crypt_memory_kb}): ")
                        ln = cons.readLine()

                        if (ln != null && ln.trim().isNotBlank()) {
                            try {
                                cryptMem = ln.trim().toInt()
                            } catch (e: Exception) {
                                println("Value must be an integer")
                            }
                        } else {
                            cryptMem = config.crypt_memory_kb
                        }
                    }
                    config.crypt_memory_kb = cryptMem

                    println("What host will the frontend be running on? (${config.frontend_host}): ")
                    ln = cons.readLine()

                    if (ln != null && ln.trim().isNotBlank())
                        config.frontend_host = ln.trim()
                }

                println("Will TwineMedia be running behind a reverse proxy? [y/N]: ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.reverse_proxy = ln.toLowerCase().startsWith("n") == false

                println("Where is ffmpeg located? (${config.ffmpeg_path}): ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.ffmpeg_path = ln.trim()

                println("Where is ffprobe located? (${config.ffprobe_path}): ")
                ln = cons.readLine()

                if (ln != null && ln.trim().isNotBlank())
                    config.ffprobe_path = ln.trim()

                var procCount: Int? = null
                while (procCount == null) {
                    println("How many files should TwineMedia be able to process at once? (${config.media_processor_count}): ")
                    ln = cons.readLine()

                    if (ln != null && ln.trim().isNotBlank()) {
                        try {
                            procCount = ln.trim().toInt()
                        } catch (e: Exception) {
                            println("Value must be an integer")
                        }
                    } else {
                        procCount = config.media_processor_count
                    }
                }
                config.media_processor_count = procCount

                if (advanced) {
                    var authMax: Int? = null
                    while (authMax == null) {
                        println("How many attempts can a user make to authenticate before they will be throttled? (${config.max_auth_attempts}): ")
                        ln = cons.readLine()

                        if (ln != null && ln.trim().isNotBlank()) {
                            try {
                                authMax = ln.trim().toInt()
                            } catch (e: Exception) {
                                println("Value must be an integer")
                            }
                        } else {
                            authMax = config.max_auth_attempts
                        }
                    }
                    config.max_auth_attempts = authMax

                    var authTimeout: Int? = null
                    while (authTimeout == null) {
                        println("How many milliseconds will users be throttled after hitting their max auth attempts? (${config.auth_timeout_period}): ")
                        ln = cons.readLine()

                        if (ln != null && ln.trim().isNotBlank()) {
                            try {
                                authTimeout = ln.trim().toInt()
                            } catch (e: Exception) {
                                println("Value must be an integer")
                            }
                        } else {
                            authTimeout = config.auth_timeout_period
                        }
                    }
                    config.auth_timeout_period = authTimeout
                }

                // Check if config exists, ask for overwrite confirmation if it does
                if (cfg.exists()) {
                    println("A configuration file already exists, replace it? [y/N]: ")
                    if (cons.readLine().toLowerCase().startsWith("y")) {
                        Writer.write("configs/twinemedia.json", Json.encodePrettily(config))
                    }
                } else {
                    Writer.write("configs/twinemedia.json", Json.encodePrettily(config))
                }

                runBlocking {
                    // Connect to database
                    println("Testing database connection...")
                    try {
                        dbInit()
                        println("Database connection successful!")
                        println("Setting up schema...")
                        dbMigrate()
                        println("Checking for an admin account...")
                        val admins = accountsModel.fetchAdminAccounts()
                        if (admins != null && admins.rows != null && admins.rows.size > 0) {
                            println("An admin account already exists")
                        } else {
                            println("No admin account exists")
                        }

                        println("Would you like to create one? [Y/n]: ")
                        if (cons.readLine().toLowerCase().startsWith("y")) {
                            createAdminPrompt()
                        }
                    } catch (e: Exception) {
                        System.err.println("Error occurred when attempting to access database, check your database settings. Error:")
                        e.printStackTrace()
                    }
                }

                println("Installation and configuration complete!")
            } else if(serverArgs().option("twinemedia-reset-admin")) {
                val cfg = File("configs/twinemedia.json")
                if (cfg.exists()) {
                    config = Json.decodeValue(Reader.read(cfg), TwineMediaConfig::class.java)
                    if (!config.upload_location.endsWith('/'))
                        config.upload_location += '/'
                } else {
                    println("TwineMedia is no configured, please run Twine with the --twinemedia-install option to configure and install everything")
                    return
                }

                println("Welcome to TwineMedia. You are about to install and configure this module.")
                println("Press enter to continue.")
                cons.readLine()

                println("Attempting to connect to the database...")
                dbInit()
                println("Connected!")

                runBlocking {
                    println("Fetching admin accounts...")
                    val adminsRes = accountsModel.fetchAdminAccounts()

                    if(adminsRes != null && adminsRes.rows.isNotEmpty()) {
                        println("Select the ID of the account you want to reset the password of:")

                        var idColWidth = 2
                        var nameColWidth = 4
                        var emailColWidth = 5

                        for(acc in adminsRes.rows) {
                            if(acc.getInteger("id").toString().length > idColWidth)
                                idColWidth = acc.getInteger("id").toString().length
                            if(acc.getString("account_name").length > nameColWidth)
                                nameColWidth = acc.getString("account_name").length
                            if(acc.getString("account_email").length > emailColWidth)
                                emailColWidth = acc.getString("account_email").length
                        }

                        // Print columns
                        print("ID")
                        repeat(idColWidth-2) { print(' ') }
                        print(" | Name")
                        repeat(nameColWidth-4) { print(' ') }
                        print(" | Email")
                        repeat(emailColWidth-5) { print(' ') }
                        println()

                        for(acc in adminsRes.rows) {
                            val id = acc.getInteger("id").toString()
                            val name = acc.getString("account_name")
                            val email = acc.getString("account_email")

                            // Print row
                            print(id)
                            repeat(idColWidth-id.length) { print(' ') }
                            print(" | $name")
                            repeat(nameColWidth-name.length) { print(' ') }
                            print(" | $email")
                            repeat(emailColWidth-email.length) { print(' ') }
                            println()
                        }

                        try {
                            print("Account ID: ")
                            val id = cons.readLine().toInt()

                            // Check if account is in list
                            var exists = false
                            for(acc in adminsRes.rows)
                                if(acc.getInteger("id") == id) {
                                    exists = true
                                    break
                                }

                            if(exists) {
                                print("New password: ")
                                val pass = cons.readPassword().joinToString("")
                                print("Confirm password: ")
                                val passConf = cons.readPassword().joinToString("")

                                if(pass == passConf) {
                                    try {
                                        println("Updating password...")
                                        updateAccountPassword(id, pass)
                                        println("Updated!")
                                    } catch(e: Exception) {
                                        System.err.println("Failed to update password because of error:")
                                        e.printStackTrace()
                                    }
                                } else {
                                    println("Passwords do not match")
                                }
                            } else {
                                println("Invalid ID")
                            }
                        } catch(e: Exception) {
                            println("Invalid ID")
                        }
                    } else {
                        println("No admin accounts exist, would you like to create one? [Y/n]: ")
                        if (cons.readLine().toLowerCase().startsWith("y")) {
                            createAdminPrompt()
                        }
                    }
                }
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

                // Run migration if db_auto_migrate is true, or if --twinemedia-migrate specified
                if(config.db_auto_migrate || serverArgs().option("twinemedia-migrate")) {
                    logger.info("Running database migrations...")
                    dbMigrate()
                }

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
                    val tagsModel = TagsModel()

                    // Refresh tags
                    logger.info("Refreshing tags...")
                    tagsModel.refreshTags()

                    logger.info("Started!")
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
    override fun twineVersion() = "1.5+"
}