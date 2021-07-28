package net.termer.twinemedia.util

import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException

// Vert.x FS
private val fs = vertx().fileSystem()

/**
 * Deletes all files in the currently processing media directory
 * @since 1.4.0
 */
@DelicateCoroutinesApi
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

/**
 * Deletes all files in the currently uploading directory
 * @since 1.5.0
 */
@DelicateCoroutinesApi
suspend fun deleteFilesInUploadingDirectory() {
	// Get files
	val files = fs.readDir(config.upload_location).await()

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

/**
 * Recursively scans a directory and returns the paths of all of its files
 * @param dir The directory to scan
 * @param ignoreErrors Whether to ignore I/O errors while scanning
 * @return All of the files contained in the specified directory and its subdirectories
 * @since 1.5.0
 */
suspend fun readDirRecursive(dir: String, ignoreErrors: Boolean = false): Array<String> {
	val fs = vertx().fileSystem()
	val paths = ArrayList<String>()
	val toIndex = arrayListOf(if(dir.endsWith('/')) dir else "$dir/")

	while(toIndex.size > 0) {
		val toRead = toIndex[0]
		toIndex.removeAt(0)

		// Get directory's contents
		val files = fs.readDir(toRead).await()

		for(path in files) {
			try {
				val props = fs.props(path).await()

				if(props.isRegularFile)
					paths.add(path)
				else if(props.isDirectory)
					toIndex.add("$path/")
			} catch(e: NoSuchFileException) {
				/* Oh well, the file no longer exists, there's nothing to do about it */
			} catch(e: FileSystemException) {
				if(!ignoreErrors)
					throw e
			}
		}
	}

	return paths.toTypedArray()
}