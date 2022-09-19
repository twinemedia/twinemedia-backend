package net.termer.twinemedia.util

import java.util.*

/**
 * Utility class that provides a "lock" functionality.
 * To create a lock, call createLock(), which returns the lock ID.
 * To delete a lock, call deleteLock(id), with the lock ID.
 * If any locks are present, locked() will return true.
 *
 * Keep in mind that after 4294967294 (Int.MIN_VALUE to Int.MAX_VALUE) locks are generated, lock IDs will repeat.
 * @author termer
 * @since 1.5.0
 */
class ConcurrentLock {
	private var curId = Int.MIN_VALUE
	private val locks = Collections.synchronizedList(ArrayList<Int>())

	/**
	 * Returns whether any locks are present
	 * @return Whether any locks are present
	 * @since 1.5.0
	 */
	@Synchronized
	fun locked() = locks.size > 0

	/**
	 * Returns all current lock IDs
	 * @return All current lock IDs
	 * @since 1.5.0
	 */
	@Synchronized
	fun locks() = locks.toTypedArray()

	/**
	 * Creates a new lock and returns its ID.
	 * The lock will continue to exist until deleteLock(id) is called with the returned ID.
	 * @return The new lock's ID
	 * @since 1.5.0
	 */
	@Synchronized
	fun createLock(): Int {
		// Increment ID
		curId++

		val id = curId

		locks.add(id)

		return id
	}

	/**
	 * Deletes the lock with the specified ID, if it exists
	 * @since 1.5.0
	 */
	@Synchronized
	fun deleteLock(id: Int) {
		locks.remove(id)
	}
}