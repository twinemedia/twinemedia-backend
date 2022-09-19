package net.termer.twinemedia.source

/**
 * Interface that defines the features of a stateful file source.
 * Stateful file sources require initialization and persistence, and are generally more expensive than a default, stateless media source.
 * Exercise caution in what code is put in a stateful file source as there may be many instances running in one application.
 * @author termer
 * @since 1.5.0
 */
interface StatefulFileSource: FileSource {
	/**
	 * Starts up and initializes this [StatefulFileSource] (should be configured first)
	 * @since 1.5.0
	 */
	suspend fun startup()

	/**
	 * Shuts down and de-initializes this [StatefulFileSource] (should already be running)
	 * @since 1.5.0
	 */
	suspend fun shutdown()
}