package net.termer.twinemedia.source.impl.fs

import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystemException
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.coroutines.await
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.source.*
import net.termer.twinemedia.source.config.SourceNotConfiguredException
import net.termer.twinemedia.source.stripInvalidFileKeyChars
import net.termer.twinemedia.util.filenameToFilenameWithUnixTimestamp
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList

class LocalDirectoryMediaSource: IndexableMediaSource {
	private val config = LocalDirectoryMediaSourceConfig()
	private val fs = vertx().fileSystem()

	override fun getConfig() = config

	/**
	 * Throws SourceNotConfiguredException if not configured
	 * @throws SourceNotConfiguredException if not configured
	 */
	private fun failIfNotConfigured() {
		if(config.directory == null)
			throw SourceNotConfiguredException("LocalDirectoryMediaSource has not been configured")
	}

	/**
	 * Returns whether the provided path is a file or not
	 * @return Whether the provided path is a file or not
	 */
	private suspend fun isFile(path: String) = try {
		fs.props(path).await().isRegularFile
	} catch(e: NoSuchFileException) {
		false
	}

	/**
	 * Checks whether a file exists with the specified key
	 * @param key The key to check for
	 * @return Whether a file exists with the specified key
	 * @since 1.5.0
	 */
	override suspend fun fileExists(key: String): Boolean {
		failIfNotConfigured()

		// Check if the file exists
		return try {
			isFile(config.directory+stripInvalidFileKeyChars(key))
		} catch(e: FileSystemException) {
			false
		}
	}

	override suspend fun listFiles(): Array<MediaSourceFile> {
		failIfNotConfigured()

		try {
			val sourceFiles = ArrayList<MediaSourceFile>()
			val toIndex = arrayListOf(if(config.directory!!.endsWith('/')) config.directory else "${config.directory}/")

			while(toIndex.size > 0) {
				val dir = toIndex[0]
				toIndex.removeAt(0)

				// Get directory's contents
				val files = fs.readDir(dir).await()

				for(path in files) {
					try {
						val props = fs.props(path).await()

						if(props.isRegularFile)
							sourceFiles.add(MediaSourceFile(
									key = path.substring(config.directory!!.length),
									size = props.size(),
									createdOn = Date(props.creationTime()).toInstant().atOffset(ZoneOffset.UTC),
									modifiedOn = Date(props.lastModifiedTime()).toInstant().atOffset(ZoneOffset.UTC)
							))
						else if(props.isDirectory)
							toIndex.add("$path/")
					} catch(e: NoSuchFileException) { /* Oh well, the file no longer exists, there's nothing to do about it */ }
				}
			}

			return sourceFiles.toTypedArray()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun getFile(key: String): MediaSourceFile {
		failIfNotConfigured()

		try {
			if(!fileExists(key))
				throw MediaSourceFileNotFoundException("File $key does not exist")

			val path = config.directory+stripInvalidFileKeyChars(key)

			// Get file properties
			val props = fs.props(path).await()

			// Return file
			return MediaSourceFile(
					key = path.substring(config.directory!!.length),
					size = props.size(),
					createdOn = Date(props.creationTime()).toInstant().atOffset(ZoneOffset.UTC),
					modifiedOn = Date(props.lastModifiedTime()).toInstant().atOffset(ZoneOffset.UTC)
			)
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun openReadStream(key: String, offset: Long, length: Long): MediaSource.StreamAndFile {
		failIfNotConfigured()

		try {
			val path = config.directory+stripInvalidFileKeyChars(key)

			// Get file info
			val props = try {
				fs.props(path).await()
			} catch(e: NoSuchFileException) {
				throw MediaSourceFileNotFoundException("File $key does not exist")
			}

			// Open stream
			val stream = fs.open(path, OpenOptions().setRead(true)).await()
					.pause()

			if(offset > -1)
				stream.setReadPos(offset)
			if(length > -1)
				stream.readLength = length

			// Return wrapped stream
			return MediaSource.StreamAndFile(stream, MediaSourceFile(
					key = path.substring(config.directory!!.length),
					size = props.size(),
					createdOn = Date(props.creationTime()).toInstant().atOffset(ZoneOffset.UTC),
					modifiedOn = Date(props.lastModifiedTime()).toInstant().atOffset(ZoneOffset.UTC)
			))
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun openWriteStream(key: String, length: Long): WriteStream<Buffer> {
		failIfNotConfigured()

		try {
			if(fileExists(key))
				throw MediaSourceFileAlreadyExistsException("File $key already exists")

			val path = config.directory+stripInvalidFileKeyChars(key)

			// Create directories if necessary
			if(key.contains('/'))
				fs.mkdirs(path.substring(0, path.lastIndexOf('/'))).await()

			return fs.open(path, OpenOptions().setWrite(true)).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun deleteFile(key: String) {
		failIfNotConfigured()

		try {
			if(!fileExists(key))
				throw MediaSourceFileNotFoundException("File $key does not exist")

			val safeKey = stripInvalidFileKeyChars(key)

			// Work out what directories need to be deleted if there's a slash
			if(safeKey.contains('/')) {
				// Delete the file
				fs.delete(config.directory+safeKey).await()

				// Get directory tree
				val dirs = ArrayList<String>()
				var tmp = safeKey
				while(tmp.contains('/')) {
					tmp = tmp.substring(0, tmp.lastIndexOf('/'))
					dirs.add(tmp)
				}

				// Delete any empty directories
				for(dir in dirs) {
					val path = config.directory+dir

					try {
						// Delete the directory if it's empty
						if(fs.readDir(path).await().size < 1)
							fs.delete(path).await()
					} catch(e: NoSuchFileException) { /* Nothing to be done about this, directory doesn't exist anymore */
					}
				}
			} else {
				// Delete the file
				fs.delete(config.directory+safeKey).await()
			}
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun downloadFile(key: String, pathOnDisk: String) {
		failIfNotConfigured()

		try {
			val sourceFile = openReadStream(key).stream
			val destFile = fs.open(pathOnDisk, OpenOptions().setWrite(true)).await()

			sourceFile.pipeTo(destFile).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}
	override suspend fun uploadFile(pathOnDisk: String, key: String) {
		failIfNotConfigured()

		try {
			val sourceFile = fs.open(pathOnDisk, OpenOptions().setRead(true)).await()
			val destFile = openWriteStream(key)

			sourceFile.pipeTo(destFile).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override fun supportsReadPosition() = true

	override fun filenameToKey(filename: String) = filenameToFilenameWithUnixTimestamp(filename)

	override suspend fun getRemainingStorage(): Long? = vertx().executeBlocking<Long> {
		failIfNotConfigured()

		try {
			it.complete(Files.getFileStore(File(config.directory!!).toPath()).usableSpace)
		} catch(e: Throwable) {
			it.fail(wrapThrowableAsMediaSourceException(e))
		}
	}.await()
}