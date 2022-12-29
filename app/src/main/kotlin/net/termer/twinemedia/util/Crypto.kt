package net.termer.twinemedia.util

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import net.termer.twinemedia.AppContext
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
 * @param vertx The [Vertx] instance to use
 * @param appCtx Application context to use
 * @since 2.0.0
 */
class Crypto(
    private val vertx: Vertx,
    private val appCtx: AppContext
) {
    companion object {
        /**
         * For internal use only. Do not use or modify.
         * @since 2.0.0
         */
        var instanceInternal: Crypto? = null

        /**
         * The global [Crypto] instance.
         * Should be used in places where a local [AppContext] object is not available.
         * @since 2.0.0
         */
        val INSTANCE
            get() = instanceInternal!!
    }

    // Argon2 instance
    private val argon2 : Argon2 = Argon2Factory.create(Argon2Types.ARGON2id)

    // AES secret
    private val aesSecret: SecretKey

    init {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(
            appCtx.config.encryptionKey.toCharArray(),
            appCtx.config.encryptionSalt.toByteArray(),
            65536,
            256
        )
        val tmp = factory.generateSecret(spec)
        aesSecret = SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Hashes (and salts) the provided password
     * @since 1.0.0
     */
    suspend fun hashPassword(password: String): String = vertx.executeBlocking {
        // Hash password using configured performance settings
        it.complete(argon2.hash(
                appCtx.config.passwordHashThreadCount,
                appCtx.config.passwordHashMemoryKib,
                appCtx.config.passwordHashThreadCount,
                password.toCharArray()
        ))
    }.await()

    /**
     * Checks if the provided password matches the specified hash
     * @since 1.0.0
     */
    suspend fun verifyPassword(password: String, hash: String): Boolean = vertx.executeBlocking {
        it.complete(argon2.verify(hash, password.toCharArray()))
    }.await()

    /**
     * Encrypts a [ByteArray] using the AES algorithm and the secret stored in this [Crypto] instance.
     * Both sides of the result string are encoded using Base64Url.
     * @param bytes The [ByteArray] to encrypt
     * @return The encrypted string
     * @since 2.0.0
     */
    suspend fun aesEncrypt(bytes: ByteArray): String = vertx.executeBlocking {
        // AES encryption cipher
        val aesEnc = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesEnc.init(Cipher.ENCRYPT_MODE, aesSecret)

        val params = aesEnc.parameters
        val iv = params.getParameterSpec(IvParameterSpec::class.java).iv
        val enc = aesEnc.doFinal(bytes)

        val encoder = Base64.getEncoder()
        val ivStr = base64ToBase64Url(String(encoder.encode(iv), UTF_8))
        val encStr = base64ToBase64Url(String(encoder.encode(enc), UTF_8))

        it.complete("$ivStr.$encStr")
    }.await()

    /**
     * Encrypts a string using the AES algorithm and the secret stored in this [Crypto] instance.
     * Both sides of the result string are encoded using Base64Url.
     * @param str The string to encrypt
     * @return The encrypted string
     * @since 2.0.0
     */
    suspend fun aesEncryptString(str: String) = aesEncrypt(str.toByteArray(UTF_8))

    /**
     * Decrypts bytes using the AES algorithm and the secret stored in this [Crypto] instance
     * @param str The encrypted string to decrypt
     * @return The decrypted bytes
     * @since 2.0.0
     */
    suspend fun aesDecrypt(str: String): ByteArray = vertx.executeBlocking {
        if(!str.contains('.'))
            throw IllegalArgumentException("Encrypted string does not contain an IV")

        val decoder = Base64.getDecoder()

        val parts = str.split('.')
        val iv = decoder.decode(base64UrlToBase64(parts[0]))
        val enc = decoder.decode(base64UrlToBase64(parts[1]))

        // AES decryption cipher
        val aesDec = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesDec.init(Cipher.DECRYPT_MODE, aesSecret, IvParameterSpec(iv))

        it.complete(aesDec.doFinal(enc))
    }.await()

    /**
     * Decrypts a string using the AES algorithm and the secret stored in this [Crypto] instance
     * @param str The encrypted string to decrypt
     * @return The decrypted string
     * @since 2.0.0
     */
    suspend fun aesDecryptString(str: String) = String(aesDecrypt(str), UTF_8)
}