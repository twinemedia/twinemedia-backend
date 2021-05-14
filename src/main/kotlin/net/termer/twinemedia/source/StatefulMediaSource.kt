package net.termer.twinemedia.source

/**
 * Interface that defines the features of a stateful media source.
 * Stateful media sources require initialization and persistence, and are generally more expensive than a default, stateless media source.
 * Exercise caution in what code is put in a stateful media source as there may be many instances running in one application.
 * @author termer
 * @since 1.5.0
 */
interface StatefulMediaSource: MediaSource {
	/**
	 * Starts up and initializes this StatefulMediaSource (should be configured first)
	 * @since 1.5.0
	 */
	suspend fun startup()

	/**
	 * Shuts down and de-initializes this StatefulMediaSource (should already be running)
	 * @since 1.5.0
	 */
	suspend fun shutdown()
}