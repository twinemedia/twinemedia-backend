package net.termer.twinemedia.source.impl.fs

import io.vertx.core.json.JsonObject
import net.termer.twinemedia.source.config.MediaSourceConfig
import net.termer.twinemedia.source.config.MediaSourceSchema
import net.termer.twinemedia.source.config.ValidationFailedException
import java.io.File

/**
 * Configuration for LocalDirectoryMediaSource.
 * The config is as follows:
 * directory (string) - The path to the directory where files are stored
 * indexSubdirs (boolean) - Whether to index subdirectories
 * @author termer
 * @since 1.5.0
 */
class LocalDirectoryMediaSourceConfig: MediaSourceConfig {
	private val schema = MediaSourceSchema(
			arrayOf(MediaSourceSchema.Section("general", "General")),
			arrayOf(
					MediaSourceSchema.Field("directory", "Directory Path", MediaSourceSchema.Field.Type.STRING, false, "/", "general"),
					MediaSourceSchema.Field("indexSubdirs", "Search Sub-Directories For Files", MediaSourceSchema.Field.Type.BOOLEAN, false, true, "general")
			)
	)

	var directory: String? = null
	var indexSubdirs = false

	override fun getSchema() = schema

	override fun configure(json: JsonObject) {
		val validationRes = schema.validate(json)

		if(validationRes.valid) {
			directory = File(json.getString("directory")).canonicalPath

			// Make sure it ends with a trailing
			if(!directory!!.endsWith("/"))
				directory += '/'

			indexSubdirs = json.getBoolean("indexSubdirs")
		} else {
			throw ValidationFailedException("Validation for the provided JSON does not match the schema: "+validationRes.errorText)
		}
	}
}