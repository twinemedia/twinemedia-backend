package net.termer.twinemedia.source.impl.s3

import software.amazon.awssdk.auth.credentials.internal.SystemSettingsCredentialsProvider
import software.amazon.awssdk.utils.SystemSetting
import java.util.*

/**
 * AWS credential provider implementation that returns credentials statically
 * @param accessKey The access key to store
 * @param secretKey The secret key to store
 * @author termer
 * @since 1.5.0
 */
class AWSCredentialProvider(private val accessKey: String, private val secretKey: String): SystemSettingsCredentialsProvider() {
	override fun loadSetting(setting: SystemSetting?): Optional<String> = Optional.ofNullable(when(setting?.property()) {
		"aws.accessKeyId" -> accessKey
		"aws.secretAccessKey" -> secretKey
		else -> null
	})
}