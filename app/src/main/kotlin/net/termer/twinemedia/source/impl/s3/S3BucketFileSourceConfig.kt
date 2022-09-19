package net.termer.twinemedia.source.impl.s3

import io.vertx.core.json.JsonObject
import net.termer.twinemedia.source.config.FileSourceConfig
import net.termer.twinemedia.source.config.FileSourceSchema
import net.termer.twinemedia.source.config.FileSourceSchema.*
import net.termer.twinemedia.source.config.ValidationFailedException

/**
 * Configuration for [S3BucketFileSource].
 *
 * The config is as follows:
 * endpoint (string) - The server endpoint URL
 * region (string) - The server region
 * bucket_name (string) - The bucket name
 * access_key (string) - The access key to use
 * secret_key (string) - The secret key to use
 *
 * @author termer
 * @since 1.5.0
 */
class S3BucketFileSourceConfig: FileSourceConfig {
	override val schema = FileSourceSchema(
			arrayOf(
				Section("general", "General"),
				Section("credentials", "Credentials"),
				Section("advanced", "Advanced")
			),
			arrayOf(
				Field(
					fieldName = "endpoint",
					name = "Endpoint URL",
					type = Field.Type.STRING,
					optional = false,
					default = "https://s3.us-east-2.amazonaws.com",
					section = "general"
				),
				Field(
					fieldName = "region",
					name = "Region",
					type = Field.Type.STRING,
					optional = false,
					default = "us-east-2",
					section = "general"
				),
				Field(
					fieldName = "bucket_name",
					name = "Bucket Name",
					type = Field.Type.STRING,
					optional = false,
					default = null,
					section = "general"
				),
				Field(
					fieldName = "access_key",
					name = "Access Key",
					type = Field.Type.STRING,
					optional = false,
					default = null,
					section = "credentials"
				),
				Field(
					fieldName = "secret_key",
					name = "Secret Key",
					type = Field.Type.STRING,
					optional = false,
					default = null,
					section = "credentials"
				)
			)
	)

	var endpoint: String? = null
	var region: String? = null
	var bucketName: String? = null
	var accessKey: String? = null
	var secretKey: String? = null

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