package net.termer.twinemedia.util

import io.vertx.kotlin.coroutines.await
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config

// Vert.x FS
private val fs = vertx().fileSystem()

/**
 * Deletes all files in the currently processing media directory
 * @since 1.4.0
 */
suspend fun deleteFilesInProcessingDirectory() {
	// Get files
	val files = fs.readDir(config.processing_location).await()

	// Delete files
	for(file in files) {
		val props = fs.props(file).await()

		// Make sure path is a regular file
		if(props.isRegularFile) {
			// Delete the file
			fs.delete(file).await()
		}
	}
}