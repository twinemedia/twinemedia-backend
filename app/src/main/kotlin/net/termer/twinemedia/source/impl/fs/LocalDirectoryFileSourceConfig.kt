package net.termer.twinemedia.source.impl.fs

import io.vertx.core.json.JsonObject
import net.termer.twinemedia.source.config.FileSourceConfig
import net.termer.twinemedia.source.config.FileSourceSchema
import net.termer.twinemedia.source.config.FileSourceSchema.*
import net.termer.twinemedia.source.config.ValidationFailedException
import java.io.File

/**
 * Configuration for [LocalDirectoryFileSource].
 *
 * The config is as follows:
 * directory (string) - The path to the directory where files are stored
 * indexSubdirs (boolean) - Whether to index subdirectories
 *
 * @author termer
 * @since 1.5.0
 */
class LocalDirectoryFileSourceConfig: FileSourceConfig {
	override val schema = FileSourceSchema(
			arrayOf(Section("general", "General")),
			arrayOf(
				Field(
					fieldName = "directory",
					name = "Directory Path",
					type = Field.Type.STRING,
					optional = false,
					default = "/",
					section = "general"
				),
				Field(
					fieldName = "index_subdirs",
					name = "Search Sub-Directories For Files",
					type = Field.Type.BOOLEAN,
					optional = false,
					default = true,
					section = "general"
				)
			)
	)

	var directory: String? = null
	var indexSubdirs = false

	override fun configure(json: JsonObject) {
		val validationRes = schema.validate(json)

		if(validationRes.valid) {
			directory = File(json.getString("directory")).canonicalPath

			// Make sure it ends with a trailing
			if(!directory!!.endsWith('/'))
				directory += '/'

			indexSubdirs = json.getBoolean("index_subdirs")
		} else {
			throw ValidationFailedException("Validation for the provided JSON does not match the schema: "+validationRes.errorText)
		}
	}
}