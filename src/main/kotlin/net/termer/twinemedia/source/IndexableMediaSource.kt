package net.termer.twinemedia.source

/**
 * Interface that defines all methods of a media source that can be indexed
 * @author termer
 * @since 1.5.0
 */
interface IndexableMediaSource: MediaSource {
	/*
	 * TODO This interface will be used to define methods for source that can be indexed and scanned.
	 * These sources will hook into the database and look for new files, automatically creating new database entries for them, and indexing them.
	 * For this release, it will do nothing.
	 */
}