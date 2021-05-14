package net.termer.twinemedia.source

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.core.streams.WriteStream
import net.termer.twinemedia.source.config.MediaSourceConfig

/**
 * Interface that defines all the basic functions of a media source.
 * Sources are in their most basic form, an object storage interface. They have no concept of directories or paths, only keys and objects.
 * No implementation of MediaSource should acknowledge directories, even if the underlying storage medium is a traditional filesystem.
 * Additionally, all MediaSources should disallow keys that are not allowed by isFileKeyValid() for writing, but should allow them for reading in the underlying storage medium contains files with invalid keys.
 * For example, a file with the character "@" in it should be able to be read, but no new files should be able to be written with that character in it.
 * Sources should not have constructors with properties; they should be configured after instantiation, and if logic needs to be performed before using them, see StatefulMediaSource.
 * @author termer
 * @since 1.5.0
 */
interface MediaSource {
	/**
	 * Data class that holds a ReadStream and the file that it's coming from
	 * @param stream The stream
	 * @param file The file the stream is coming from
	 * @author termer
	 * @since 1.5.0
	 */
	class StreamAndFile(val stream: ReadStream<Buffer>, val file: MediaSourceFile)

	/**
	 * Returns this MediaSource's configuration
	 * @return This MediaSource's configuration
	 * @since 1.5.0
	 */
	fun getConfig(): MediaSourceConfig

	/**
	 * Returns whether a file with the specified key exists
	 * @param key The key to check for
	 * @return Whether a file with the specified key exists
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun fileExists(key: String): Boolean

	/**
	 * Returns all available files in this MediaSource
	 * @return All available files in this MediaSource
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun listFiles(): Array<MediaSourceFile>

	/**
	 * Returns information about the file with the specified key in this MediaSource
	 * @param key The file key
	 * @return Information about the file with the specified key in this MediaSource
	 * @throws MediaSourceFileNotFoundException If the file does not exist
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun getFile(key: String): MediaSourceFile

	/**
	 * Opens a [ReadStream] from the file with the specified key.
	 * Note that streams are returned in fetch mode by default, and need to call [ReadStream.resume] to switch to flowing mode.
	 * @param key The file's key
	 * @param offset The offset to read, or -1 for none (only applies if supported by the media source)
	 * @param length The length to read, or -1 for all (only applies if supported by the media source)
	 * @return The newly opened ReadStream
	 * @throws MediaSourceFileNotFoundException If the file does not exist
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun openReadStream(key: String, offset: Long = -1, length: Long = -1): StreamAndFile

	/**
	 * Opens a [WriteStream] to the file with the specified key
	 * @param key The file's key
	 * @param length The length of the content that is being written (required for many sources)
	 * @return The newly opened WriteStream
	 * @throws MediaSourceFileAlreadyExistsException If the file already exists
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun openWriteStream(key: String, length: Long = -1): WriteStream<Buffer>

	/**
	 * Deletes the file with the specified key
	 * @param key The key of the file to delete
	 * @throws MediaSourceFileNotFoundException If the file does not exist
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun deleteFile(key: String)

	/**
	 * Utility method that downloads the file with the specified key to the provided path on disk
	 * @param key The key of the file to download
	 * @param pathOnDisk The path on disk to write the file to
	 * @throws MediaSourceFileNotFoundException If the file does not exist
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun downloadFile(key: String, pathOnDisk: String)

	/**
	 * Utility method that uploads a file from disk to a new on this MediaSource with the specified key
	 * @param pathOnDisk The path on disk of the file to upload
	 * @param key The key on this MediaSource to upload file as
	 * @throws MediaSourceFileAlreadyExistsException If the file already exists
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun uploadFile(pathOnDisk: String, key: String)

	/**
	 * Returns whether this source supports reading a file from a specific byte position
	 * @return Whether this source supports reading a file from a specific byte position
	 * @since 1.5.0
	 */
	fun supportsReadPosition(): Boolean

	/**
	 * Converts a provided filename into a suitable key
	 * @param filename The filename to convert
	 * @return A suitable key for the provided filename
	 * @since 1.5.0
	 */
	fun filenameToKey(filename: String): String

	/**
	 * Returns the source's remaining storage space (or null if there is no limit)
	 * @return The source's remaining storage space
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun getRemainingStorage(): Long?
}