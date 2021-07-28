package net.termer.twinemedia.source.impl.fs

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystemException
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.coroutines.await
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.source.*
import net.termer.twinemedia.source.config.SourceNotConfiguredException
import net.termer.twinemedia.source.stripInvalidFileKeyChars
import net.termer.twinemedia.util.ConcurrentLock
import net.termer.twinemedia.util.filenameToFilenameWithUnixTimestamp
import java.nio.file.NoSuchFileException
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList

class LocalDirectoryMediaSource: IndexableMediaSource {
	private val config = LocalDirectoryMediaSourceConfig()
	private val fs = vertx().fileSystem()
	private val lock = ConcurrentLock()

	override fun getConfig() = config

	override fun getLock() = lock

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
	private suspend fun isFile(path: String): Boolean {
		var res = false

		val lockId = lock.createLock()

		try {
			res = fs.props(path).await().isRegularFile
		} catch(e: NoSuchFileException) {
			// File doesn't exist
		} catch(e: FileSystemException) {
			if(e.cause !is NoSuchFileException)
				throw e
		}

		lock.deleteLock(lockId)

		return res
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

		val lockId = lock.createLock()

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
			throw wrapThrowableAsMediaSourceException(e)
		}
	}

	override suspend fun getFile(key: String): MediaSourceFile {
		failIfNotConfigured()

		val lockId = lock.createLock()

		try {
			if(!fileExists(key))
				throw MediaSourceFileNotFoundException("File $key does not exist")

			val path = config.directory+stripInvalidFileKeyChars(key)

			// Get file properties
			val props = fs.props(path).await()

			lock.deleteLock(lockId)

			// Return file
			return MediaSourceFile(
					key = path.substring(config.directory!!.length),
					size = props.size(),
					createdOn = Date(props.creationTime()).toInstant().atOffset(ZoneOffset.UTC),
					modifiedOn = Date(props.lastModifiedTime()).toInstant().atOffset(ZoneOffset.UTC)
			)
		} catch(e: Throwable) {
			lock.deleteLock(lockId)
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
			} catch(e: FileSystemException) {
				if(e.cause is NoSuchFileException)
					throw MediaSourceFileNotFoundException("File $key does not exist")
				else
					throw e
			} catch(e: NoSuchFileException) {
				throw MediaSourceFileNotFoundException("File $key does not exist")
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
			return MediaSource.StreamAndFile(
					object: CloseableReadStream<Buffer> {
						override fun close() = stream.close()
						override fun exceptionHandler(handler: Handler<Throwable>?) = stream.exceptionHandler(handler)
						override fun handler(handler: Handler<Buffer>?) = stream.handler(handler)
						override fun pause() = stream.pause()
						override fun resume() = stream.resume()
						override fun fetch(length: Long) = stream.fetch(length)
						override fun endHandler(handler: Handler<Void>?) = stream.endHandler(handler)
					},
					MediaSourceFile(
							key = path.substring(config.directory!!.length),
							size = props.size(),
							createdOn = Date(props.creationTime()).toInstant().atOffset(ZoneOffset.UTC),
							modifiedOn = Date(props.lastModifiedTime()).toInstant().atOffset(ZoneOffset.UTC)
					)
			)
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

		val lockId = lock.createLock()

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
			throw wrapThrowableAsMediaSourceException(e)
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
			throw wrapThrowableAsMediaSourceException(e)
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
			throw wrapThrowableAsMediaSourceException(e)
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
			throw wrapThrowableAsMediaSourceException(e)
		}
	}
}