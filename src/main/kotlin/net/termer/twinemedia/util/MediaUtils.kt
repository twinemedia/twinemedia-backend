package net.termer.twinemedia.util

import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.Module.Companion.mediaSourceManager
import net.termer.twinemedia.db.dataobject.Media
import net.termer.twinemedia.exception.ListDeleteException
import net.termer.twinemedia.exception.MediaDeleteException
import net.termer.twinemedia.exception.MediaFetchException
import net.termer.twinemedia.model.ListsModel
import net.termer.twinemedia.model.MediaModel
import net.termer.twinemedia.source.MediaSourceFileNotFoundException
import net.termer.twinemedia.source.SourceInstanceNotRegisteredException

/**
 * Deletes a media entry and file
 * @param media The media entry to delete
 * @param deleteChildren Whether to delete the media entry's children (does not apply if the media entry itself is a child of another)
 * @param mediaModel The MediaModel to use for deleting media entries
 * @param listsModel The ListsModel to use for deleting list entries linking to deleted media entries
 * @since 1.5.2
 */
@DelicateCoroutinesApi
suspend fun deleteMedia(media: Media, deleteChildren: Boolean = true, mediaModel: MediaModel = MediaModel(), listsModel: ListsModel = ListsModel()) {
	// Cancel any queued processing jobs referencing this media
	cancelQueuedProcessingJobsByParent(media.id)

	// Fetch media's source
	val source = try {
		mediaSourceManager.getSourceInstanceById(media.source)!!
	} catch(e: NullPointerException) {
		throw SourceInstanceNotRegisteredException("Tried to fetch media source ID ${media.source}, but it was not registered")
	}

	val fs = vertx().fileSystem()

	// Whether this media's files should be deleted
	var delete = false

	// Check if media is a child
	if(media.parent == null) {
		// Check if files with the same hash exist
		val hashMediaRes = mediaModel.fetchMediaByHashAndSource(media.hash, media.source)

		// If this is the only instance of the file in the source, delete the underlying file in the source
		if(hashMediaRes.count() < 2)
			delete = true

		// Only delete children if enabled
		if(deleteChildren) {
			try {
				// Fetch children
				val children = mediaModel.fetchMediaChildren(media.internalId)

				// Delete children
				for (child in children) {
					try {
						// Delete file
						try {
							source.deleteFile(child.key)
						} catch (e: MediaSourceFileNotFoundException) {
							logger.warn("Tried to delete child file entry with key ${child.key}, but it did not exist")
						}

						// Check if the child has a thumbnail
						if (child.hasThumbnail) {
							val thumbFile = Module.config.thumbnails_location + child.thumbnailFile

							// Delete thumbnail
							if (fs.exists(thumbFile).await())
								fs.delete(thumbFile).await()
						}

						// Delete entry
						mediaModel.deleteMedia(child.id)

						try {
							// Delete entries linking to the file in lists
							listsModel.deleteListItemsByMediaId(child.internalId)
						} catch (e: Exception) {
							throw ListDeleteException("Failed to delete list references to child ID ${child.id}:", e)
						}
					} catch (e: Exception) {
						throw MediaDeleteException("Failed to delete child ID ${child.id}:", e)
					}
				}
			} catch (e: Exception) {
				throw MediaFetchException("Failed to fetch media children:", e)
			}
		}
	} else {
		// Since children aren't checked for hash when being uploaded, it's safe to just delete their underlying file without checking
		delete = true
	}

	if(delete) {
		// Delete file
		try {
			source.deleteFile(media.key)
		} catch(e: MediaSourceFileNotFoundException) {
			logger.warn("Tried to delete file with key ${media.key}, but it did not exist")
		}
		// Delete thumbnail file if present
		if(media.hasThumbnail) {
			val file = media.thumbnailFile

			if(fs.exists(Module.config.thumbnails_location + file).await())
				fs.delete(Module.config.thumbnails_location + file).await()
		}
	}

	try {
		// Delete database entry
		mediaModel.deleteMedia(media.id)

		try {
			// Delete entries linking to the file in lists
			listsModel.deleteListItemsByMediaId(media.internalId)
		} catch(e: Exception) {
			throw ListDeleteException("Failed to delete list references to media ID ${media.id}:", e)
		}
	} catch(e: Exception) {
		throw MediaDeleteException("Failed to delete file entry for ID ${media.id}:", e)
	}
}