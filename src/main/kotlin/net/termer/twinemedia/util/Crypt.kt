package net.termer.twinemedia.util

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.ServerManager.vertx
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8


/**
 * Utility class for dealing with hashing and other cryptographic tasks
 * @param password The password to use for AES encryption
 * @param salt The salt to use for AES encryption
 * @since 1.5.0
 */
@DelicateCoroutinesApi
class Crypt(password: String = generateString(26), salt: ByteArray = generateString(8).toByteArray()) {
    // Argon2 instance
    private val argon2 : Argon2 = Argon2Factory.create(Argon2Types.ARGON2id)

    // AES secret
    private val aesSecret: SecretKey

    init {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        aesSecret = SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Hashes (and salts) the provided password
     * @since 1.0
     */
    suspend fun hashPassword(password: String): String = vertx().executeBlocking<String> {
        // Hash password using configured performance settings
        it.complete(argon2.hash(
                config.crypt_processor_count,
                config.crypt_memory_kb,
                config.crypt_processor_count * 2,
                password.toCharArray()
        ))
    }.await()

    /**
     * Checks if the provided password matches the specified hash
     * @since 1.0
     */
    suspend fun verifyPassword(password: String, hash: String): Boolean = vertx().executeBlocking<Boolean> {
        it.complete(argon2.verify(hash, password.toCharArray()))
    }.await()

    /**
     * Encrypts a string using the AES algorithm and the secret stored in this Crypt instance
     * @param str The string to encrypt
     * @return The encrypted string
     * @since 1.5.0
     */
    suspend fun aesEncrypt(str: String): String = vertx().executeBlocking<String> {
        // AES encryption cipher
        val aesEnc = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesEnc.init(Cipher.ENCRYPT_MODE, aesSecret)

        val params = aesEnc.parameters
        val iv = params.getParameterSpec(IvParameterSpec::class.java).iv
        val enc = aesEnc.doFinal(str.toByteArray(UTF_8))

        val encoder = Base64.getEncoder()
        val ivStr = String(encoder.encode(iv), UTF_8).replace('+', '-')
        val encStr = String(encoder.encode(enc), UTF_8).replace('+', '-')

        it.complete("$ivStr.$encStr")
    }.await()

    /**
     * Decrypts a string using the AES algorithm and the secret stored in this Crypt instance
     * @param str The encrypted string to decrypt
     * @return The decrypted string
     * @since 1.5.0
     */
    suspend fun aesDecrypt(str: String): String = vertx().executeBlocking<String> {
        if(!str.contains('.'))
            throw IllegalArgumentException("Encrypted string does not contain an IV")

        val decoder = Base64.getDecoder()

        val parts = str.split('.')
        val iv = decoder.decode(parts[0].replace('-', '+'))
        val enc = decoder.decode(parts[1].replace('-', '+'))

        // AES decryption cipher
        val aesDec = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesDec.init(Cipher.DECRYPT_MODE, aesSecret, IvParameterSpec(iv))

        it.complete(String(aesDec.doFinal(enc), UTF_8))
    }.await()
}