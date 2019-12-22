package net.termer.twinemedia.util

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types
import io.vertx.kotlin.core.executeBlockingAwait
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config

/**
 * Utility class for dealing with hashing and other cryptographic tasks
 * @since 1.0
 */
class Crypt {
    // Argon2 instance
    private val argon2 : Argon2 = Argon2Factory.create(Argon2Types.ARGON2id)

    /**
     * Hashes (and salts) the provided password
     * @since 1.0
     */
    suspend fun hashPassword(password : String) : String? {
        return vertx().executeBlockingAwait<String> {
            // Hash password using configured performance settings
            it.complete(argon2.hash(
                    config.crypt_processor_count,
                    config.crypt_memory_kb,
                    config.crypt_processor_count * 2,
                    password.toCharArray()
            ))
        }
    }

    /**
     * Checks if the provided password matches the specified hash
     * @since 1.0
     */
    suspend fun verifyPassword(password : String, hash : String) : Boolean? {
        return vertx().executeBlockingAwait<Boolean> {
            it.complete(argon2.verify(hash, password.toCharArray()))
        }
    }
}