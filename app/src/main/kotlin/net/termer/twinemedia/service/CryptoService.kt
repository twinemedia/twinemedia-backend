package net.termer.twinemedia.service

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import net.termer.twinemedia.AppConfig
import net.termer.twinemedia.util.base64ToBase64Url
import net.termer.twinemedia.util.base64UrlToBase64
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

/**
 * Service for dealing with hashing and other cryptographic tasks
 * @since 2.0.0
 */
class CryptoService(
    /**
     * The Vert.x instance to use
     */
    private val vertx: Vertx,

    /**
     * The number of threads to use for password hashing
     */
    private val passwordHashThreadCount: Int,

    /**
     * The amount of memory to use for password hashing, in kibibytes
     */
    private val passwordHashMemoryKib: Int,

    /**
     * An ideally 26 character long encryption key for the application to use.
     * This value must be secure, otherwise bad actors could decrypt sensitive data and create their own trusted payloads.
     * If this value or [encryptionSalt] changes, various values such as pagination tokens will be invalidated.
     * Care must be exercised when changing these values.
     */
    private val encryptionKey: String,

    /**
     * An ideally 8 character long encryption salt for application use.
     * If this value or [encryptionKey] changes, various values such as pagination tokens will be invalidated.
     * Care must be exercised when changing these values.
     */
    private val encryptionSalt: String
) {
    companion object {
        private var _instance: CryptoService? = null

        /**
         * The global [CryptoService] instance.
         * Will throw [NullPointerException] if accessed before [initInstance] is called.
         * @since 2.0.0
         */
        val INSTANCE
            get() = _instance!!

        /**
         * Initializes the global [INSTANCE] singleton
         * @param config The [AppConfig] to use for the singleton creation
         * @since 2.0.0
         */
        suspend fun initInstance(vertx: Vertx, config: AppConfig) {
            _instance = CryptoService(
                vertx,
                config.passwordHashThreadCount,
                config.passwordHashMemoryKib,
                config.encryptionKey,
                config.encryptionSalt
            )
        }
    }

    // Argon2 instance
    private val argon2 : Argon2 = Argon2Factory.create(Argon2Types.ARGON2id)

    // AES secret
    private val aesSecret: SecretKey

    init {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(
            encryptionKey.toCharArray(),
            encryptionSalt.toByteArray(),
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
                passwordHashThreadCount,
                passwordHashMemoryKib,
                passwordHashThreadCount,
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
     * Encrypts a [ByteArray] using the AES algorithm and the secret stored in this [CryptoService] instance.
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
     * Encrypts a string using the AES algorithm and the secret stored in this [CryptoService] instance.
     * Both sides of the result string are encoded using Base64Url.
     * @param str The string to encrypt
     * @return The encrypted string
     * @since 2.0.0
     */
    suspend fun aesEncryptString(str: String) = aesEncrypt(str.toByteArray(UTF_8))

    /**
     * Decrypts bytes using the AES algorithm and the secret stored in this [CryptoService] instance
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
     * Decrypts a string using the AES algorithm and the secret stored in this [CryptoService] instance
     * @param str The encrypted string to decrypt
     * @return The decrypted string
     * @since 2.0.0
     */
    suspend fun aesDecryptString(str: String) = String(aesDecrypt(str), UTF_8)
}
