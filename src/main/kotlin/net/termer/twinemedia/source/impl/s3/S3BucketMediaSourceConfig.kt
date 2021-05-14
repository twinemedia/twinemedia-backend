package net.termer.twinemedia.source.impl.s3

import io.vertx.core.json.JsonObject
import net.termer.twinemedia.source.config.MediaSourceConfig
import net.termer.twinemedia.source.config.MediaSourceSchema
import net.termer.twinemedia.source.config.ValidationFailedException

/**
 * Configuration for S3BucketMediaSource.
 * The config is as follows:
 * endpoint (string) - The server endpoint URL
 * region (string) - The server region
 * bucket_name (string) - The bucket name
 * access_key (string) - The access key to use
 * secret_key (string) - The secret key to use
 * @author termer
 * @since 1.5.0
 */
class S3BucketMediaSourceConfig: MediaSourceConfig {
	private val schema = MediaSourceSchema(
			arrayOf(
					MediaSourceSchema.Section("general", "General"),
					MediaSourceSchema.Section("credentials", "Credentials"),
					MediaSourceSchema.Section("advanced", "Advanced")
			),
			arrayOf(
					MediaSourceSchema.Field("endpoint", "Endpoint URL", MediaSourceSchema.Field.Type.STRING, false, "https://s3.us-east-2.amazonaws.com", "general"),
					MediaSourceSchema.Field("region", "Region", MediaSourceSchema.Field.Type.STRING, false, "us-east-2", "general"),
					MediaSourceSchema.Field("bucket_name", "Bucket Name", MediaSourceSchema.Field.Type.STRING, false, null, "general"),
					MediaSourceSchema.Field("access_key", "Access Key", MediaSourceSchema.Field.Type.STRING, false, null, "credentials"),
					MediaSourceSchema.Field("secret_key", "Secret Key", MediaSourceSchema.Field.Type.STRING, false, null, "credentials")
			)
	)

	var endpoint: String? = null
	var region: String? = null
	var bucketName: String? = null
	var accessKey: String? = null
	var secretKey: String? = null

	override fun getSchema() = schema

	override fun configure(json: JsonObject) {
		val validationRes = schema.validate(json)

		if(validationRes.valid) {
			endpoint = json.getString("endpoint")
			region = json.getString("region")
			bucketName = json.getString("bucket_name")
			accessKey = json.getString("access_key")
			secretKey = json.getString("secret_key")
		} else {
			throw ValidationFailedException("Validation for the provided JSON does not match the schema: "+validationRes.errorText)
		}
	}
}