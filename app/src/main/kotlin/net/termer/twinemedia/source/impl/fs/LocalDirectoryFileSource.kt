package net.termer.twinemedia.source.impl.fs

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystemException
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.coroutines.await
import net.termer.twinemedia.source.*
import net.termer.twinemedia.source.config.SourceNotConfiguredException
import net.termer.twinemedia.util.ConcurrentLock
import net.termer.twinemedia.util.filenameToFilenameWithUnixTimestamp
import net.termer.twinemedia.util.stripInvalidFileKeyChars
import net.termer.twinemedia.util.wrapThrowableAsFileSourceException
import java.nio.file.NoSuchFileException
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList

class LocalDirectoryFileSource(override val vertx: Vertx): IndexableFileSource {
	private val fs = vertx.fileSystem()

	override val config = LocalDirectoryFileSourceConfig()
	override val lock = ConcurrentLock()

	/**
	 * Throws [SourceNotConfiguredException] if not configured
	 * @throws SourceNotConfiguredException if not configured
	 */
	private fun failIfNotConfigured() {
		if(config.directory == null)
			throw SourceNotConfiguredException("LocalDirectoryFileSource has not been configured")
	}

	/**
	 * Returns whether the provided path points to a regular file or not
	 * @return Whether the provided path points to a regular file or not
	 */
	private suspend fun isFile(path: String): Boolean {
		val lockId = lock.createLock()

		try {
			return fs.props(path).await().isRegularFile
		} catch(e: NoSuchFileException) {
			// File doesn't exist
		} catch(e: FileSystemException) {
			if(e.cause !is NoSuchFileException)
				throw e
		} finally {
			lock.deleteLock(lockId)
		}

		// If it didn't return by now, the path doesn't point to a file
		return false
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

	override suspend fun listFiles(): Array<FileSourceFile> {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val sourceFiles = ArrayList<FileSourceFile>()
			val toIndex = arrayListOf(
				if(config.directory!!.endsWith('/'))
					config.directory
				else
					config.directory+'/'
			)

			while(toIndex.size > 0) {
				val dir = toIndex[0]
				toIndex.removeAt(0)

				// Get directory's contents
				val files = fs.readDir(dir).await()

				for(path in files) {
					try {
						val props = fs.props(path).await()

						if(props.isRegularFile)
							sourceFiles.add(FileSourceFile(
								key = path.substring(config.directory!!.length),
								size = props.size(),
								createdTs = Date(props.creationTime())
									.toInstant()
									.atOffset(ZoneOffset.UTC),
								modifiedTs = Date(props.lastModifiedTime())
									.toInstant()
									.atOffset(ZoneOffset.UTC)
							))
						else if(props.isDirectory)
							toIndex.add("$path/")
					} catch(e: NoSuchFileException) {
						// Oh well, the file no longer exists, there's nothing to do about it
					} catch(e: FileSystemException) {
						if(e.cause !is NoSuchFileException)
							throw e
					}
				}
			}

			lock.deleteLock(lockId)

			return sourceFiles.toTypedArray()
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun getFile(key: String): FileSourceFile {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			if(!fileExists(key))
				throw FileSourceFileNotFoundException("File with key \"$key\" does not exist")

			val path = config.directory+stripInvalidFileKeyChars(key)

			// Get file properties
			val props = fs.props(path).await()

			lock.deleteLock(lockId)

			// Return file
			return FileSourceFile(
				key = path.substring(config.directory!!.length),
				size = props.size(),
				createdTs = Date(props.creationTime())
					.toInstant()
					.atOffset(ZoneOffset.UTC),
				modifiedTs = Date(props.lastModifiedTime())
					.toInstant()
					.atOffset(ZoneOffset.UTC)
			)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun openReadStream(key: String, offset: Long, length: Long): FileSource.StreamAndFile {
		failIfNotConfigured()

		try {
			val path = config.directory+stripInvalidFileKeyChars(key)

			// Get file info
			val props = try {
				fs.props(path).await()
			} catch(e: FileSystemException) {
				if(e.cause is NoSuchFileException)
					throw FileSourceFileNotFoundException("File with key \"$key\" does not exist")
				else
					throw e
			} catch(e: NoSuchFileException) {
				throw FileSourceFileNotFoundException("File with key \"$key\" does not exist")
			}

			// Open stream
			val stream = fs.open(path, OpenOptions().setRead(true)).await()
					.pause()

			val correctedOffset = offset.coerceAtMost(props.size())
			if(offset > -1)
				stream.setReadPos(correctedOffset)
			if(length > -1)
				stream.readLength = length.coerceAtMost(props.size()-correctedOffset)

			// Return wrapped stream
			return FileSource.StreamAndFile(
					object: CloseableReadStream<Buffer> {
						override fun close() = stream.close()
						override fun exceptionHandler(handler: Handler<Throwable>?) = stream.exceptionHandler(handler)
						override fun handler(handler: Handler<Buffer>?) = stream.handler(handler)
						override fun pause() = stream.pause()
						override fun resume() = stream.resume()
						override fun fetch(length: Long) = stream.fetch(length)
						override fun endHandler(handler: Handler<Void>?) = stream.endHandler(handler)
					},
					FileSourceFile(
						key = path.substring(config.directory!!.length),
						size = props.size(),
						createdTs = Date(props.creationTime())
							.toInstant()
							.atOffset(ZoneOffset.UTC),
						modifiedTs = Date(props.lastModifiedTime())
							.toInstant()
							.atOffset(ZoneOffset.UTC)
					)
			)
		} catch(e: Throwable) {
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun openWriteStream(key: String, length: Long): WriteStream<Buffer> {
		failIfNotConfigured()

		try {
			if(fileExists(key))
				throw FileSourceFileAlreadyExistsException("File with key \"$key\" already exists")

			val path = config.directory+stripInvalidFileKeyChars(key)

			// Create directories if necessary
			if(key.contains('/'))
				fs.mkdirs(path.substring(0, path.lastIndexOf('/'))).await()

			return fs.open(path, OpenOptions().setWrite(true)).await()
		} catch(e: Throwable) {
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun deleteFile(key: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			if(!fileExists(key))
				throw FileSourceFileNotFoundException("File with key \"$key\" does not exist")

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
					} catch(e: NoSuchFileException) {
						// Nothing to be done about this, directory doesn't exist anymore
					} catch(e: FileSystemException) {
						if(e.cause !is NoSuchFileException)
							throw e
					}
				}
			} else {
				// Delete the file
				fs.delete(config.directory+safeKey).await()
			}

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override suspend fun downloadFile(key: String, pathOnDisk: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val sourceFile = openReadStream(key).stream
			val destFile = fs.open(pathOnDisk, OpenOptions().setWrite(true)).await()

			sourceFile.pipeTo(destFile).await()

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}
	override suspend fun uploadFile(pathOnDisk: String, key: String) {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val sourceFile = fs.open(pathOnDisk, OpenOptions().setRead(true)).await()
			val destFile = openWriteStream(key)

			sourceFile.pipeTo(destFile).await()

			lock.deleteLock(lockId)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			throw wrapThrowableAsFileSourceException(e)
		}
	}

	override fun supportsReadPosition() = true

	override fun filenameToKey(filename: String) = filenameToFilenameWithUnixTimestamp(filename)

	override suspend fun getRemainingStorage(): Long? {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			val fsProps = fs.fsProps(config.directory!!).await()

			lock.deleteLock(lockId)
			return fsProps.usableSpace()
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
			if(e is FileSystemException && e.cause is NoSuchFileException)
				return null
			throw wrapThrowableAsFileSourceException(e)
		}
	}
}