package net.termer.twinemedia.source

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.core.streams.WriteStream
import net.termer.twinemedia.source.config.MediaSourceConfig
import net.termer.twinemedia.util.ConcurrentLock

/**
 * Interface that defines all the basic functions of a media source.
 * Sources are in their most basic form, an object storage interface. They have no concept of directories or paths, only keys and objects.
 * No implementation of MediaSource should acknowledge directories, even if the underlying storage medium is a traditional filesystem.
 * Additionally, all MediaSources should disallow keys that are not allowed by isFileKeyValid() for writing, but should allow them for reading in the underlying storage medium contains files with invalid keys.
 * For example, a file with the character "@" in it should be able to be read, but no new files should be able to be written with that character in it.
 * Sources should not have constructors with properties; they should be configured after instantiation, and if logic needs to be performed before using them, see StatefulMediaSource.
 *
 * MediaSource instances should have a ConcurrentLock associated with them, which is responsible for telling the MediaSourceManager responsible for it whether or not it is safe to unload or shut down.
 * If any locks are present, the source will not be unloaded or shut down. For this reason, it is very important to be cautious when using locks, and be diligent to remove them.
 * If there is no mention of the caller handling locks in a method's documentation, IT IS UP TO THE IMPLEMENTATION TO HANDLE LOCKING.
 * This is the case for almost all methods that do not expose streams. Make sure to check the documentation, and always lock and unlock if possible.
 * The only times when it is not appropriate to lock is when it is impossible to know when to unlock, as is the case with methods putting the responsibility on the caller.
 * @author termer
 * @since 1.5.0
 */
interface MediaSource {
	/**
	 * Data class that holds a CloseableReadStream and the file that it's coming from
	 * @param stream The stream
	 * @param file The file the stream is coming from
	 * @author termer
	 * @since 1.5.0
	 */
	class StreamAndFile(val stream: CloseableReadStream<Buffer>, val file: MediaSourceFile)

	/**
	 * Returns this MediaSource's configuration
	 * @return This MediaSource's configuration
	 * @since 1.5.0
	 */
	fun getConfig(): MediaSourceConfig

	/**
	 * Returns the source's ConcurrentLock.
	 * While any locks are present, the source will not be unloaded, or in the case of StatefulMediaSource instances, shut down.
	 * Care should be taken when creating locks, because failure to remove them when finished will result in the instance never being unloaded or shut down.
	 *
	 * Locks may be used internally or externally, and are important to use while ongoing actions are being performed, such as uploads or downloads.
	 * This is especially important for StatefulMediaSource instances, as otherwise their underlying connections may be closed during an upload, for example.
	 *
	 * If creating a lock for
	 * @return The source's ConcurrentLock
	 * @since 1.5.0
	 */
	fun getLock(): ConcurrentLock

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
	 * A lock should be created before calling this method, and it should be deleted after it has finished, either successfully or otherwise.
	 * Streams states cannot be easily tracked by underlying MediaSource implementations, so it is up to the caller to lock and unlock the source to avoid being unloaded or shut down mid-operation.
	 *
	 * The file in the [StreamAndFile] returned by this method may or may not contain as detailed information as that returned by [getFile], and in fact may return no useful metadata at all.
	 * If detailed information is required, [getFile] should be used.
	 * The metadata returned from this method is used by TwineMedia to serve more accurate information when serving files, so a lack of important information will just mean that such information will by pulled from the Media entry of the file being served.
	 * NEVER send information that may be wrong in this method. If information is a guess or not guaranteed to be correct, DO NOT SEND IT.
	 * No information is better than incorrect information.
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
	 * Opens a [WriteStream] to the file with the specified key.
	 * A lock should be created before calling this method, and it should be deleted after it has finished, either successfully or otherwise.
	 * Streams states cannot be easily tracked by underlying MediaSource implementations, so it is up to the caller to lock and unlock the source to avoid being unloaded or shut down mid-operation.
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
	 * Returns the source's remaining storage space (or null if there is no limit or it cannot be determined)
	 * @return The source's remaining storage space
	 * @throws MediaSourceException If an error occurs with the underlying media source
	 * @since 1.5.0
	 */
	suspend fun getRemainingStorage(): Long?
}