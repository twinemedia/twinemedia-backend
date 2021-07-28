package net.termer.twinemedia.task

import io.vertx.core.Handler
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.DelicateCoroutinesApi
import java.time.OffsetDateTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Class that represents a task and stores information about it
 * @author termer
 * @since 1.5.0
 */
@DelicateCoroutinesApi
class Task(
		/**
		 * The task's assigned TaskManager
		 * @since 1.5.0
		 */
		val manager: TaskManager,

		/**
		 * The task's ID
		 * @since 1.5.0
		 */
		val id: Int,

		/**
		 * The task's name
		 * @since 1.5.0
		 */
		val name: String,

		/**
		 * The permission required to view the task (null if none is required, does not apply to explicitly added subjects)
		 * @since 1.5.0
		 */
		val viewPermission: String?,

		/**
		 * Whether the task is cancellable
		 * @since 1.5.0
		 */
		val isCancellable: Boolean,

		/**
		 * Handler to be run when a cancellation request is issued
		 * @since 1.5.0
		 */
		val cancelRequestHandler: Handler<Void>?,

		/**
		 * The permission required to cancel the task (null if task is not cancellable)
		 * @since 1.5.0
		 */
		val cancelPermission: String?,

		/**
		 * Whether the task is viewable by everyone (minus those without appropriate permissions)
		 * @since 1.5.0
		 */
		val isGlobal: Boolean,

		/**
		 * How progress should be displayed
		 * @since 1.5.0
		 */
		val progressType: ProgressType,

		/**
		 * The task's initial subjects (account IDs that will receive events for and can view the task)
		 * @since 1.5.0
		 */
		private val initialSubjects: Array<Int> = emptyArray(),

		/**
		 * When the task was created
		 * @since 1.5.0
		 */
		val createdOn: OffsetDateTime = OffsetDateTime.now()
) {
	/**
	 * The type of progress this task uses
	 * @author termer
	 * @since 1.5.0
	 */
	enum class ProgressType {
		/**
		 * Calculates the percentage from the completed item count and total item count
		 * @since 1.5.0
		 */
		PERCENTAGE,

		/**
		 * Simply displays the completed item count and total item count
		 * @since 1.5.0
		 */
		ITEM_COUNT
	}

	// The current account IDs that should be able to view the task and receive events for it
	private val _subjects = CopyOnWriteArrayList<Int>().apply { addAll(initialSubjects) }
	// The amount of finished items
	private var _finishedItems = 0
	// The total amount of items
	private var _totalItems: Int? = null
	// The task's current subtask
	private var _subtask: String? = null
	// Whether the task succeeded
	private var _succeeded = false
	// Whether the task is currently being cancelled
	private var _cancelling = false
	// Whether the task has been cancelled
	private var _cancelled = false
	// Whether the task failed
	private var _failed = false

	// Handlers to be called when the task successfully finishes
	private val successHandlers = CopyOnWriteArrayList<Handler<Void>>()
	// Handlers to be called when the task is successfully cancelled
	private val cancelHandlers = CopyOnWriteArrayList<Handler<Void>>()
	// Handlers to be called when the task fails
	private val failureHandlers = CopyOnWriteArrayList<Handler<Throwable?>>()

	/**
	 * Registers a handler to be run when the task succeeds
	 * @param hdlr The handler
	 * @since 1.5.0
	 */
	fun onSuccess(hdlr: Handler<Void>) {
		successHandlers.add(hdlr)
	}

	/**
	 * Registers a handler to be run when the task is cancelled
	 * @param hdlr The handler
	 * @since 1.5.0
	 */
	fun onCancel(hdlr: Handler<Void>) {
		cancelHandlers.add(hdlr)
	}

	/**
	 * Registers a handler to be run when the task fails
	 * @param hdlr The handler
	 * @since 1.5.0
	 */
	fun onFail(hdlr: Handler<Throwable?>) {
		failureHandlers.add(hdlr)
	}

	/**
	 * The amount of finished items
	 * @since 1.5.0
	 */
	var finishedItems
		get() = _finishedItems
		set(value) {
			_finishedItems = value
			manager.broadcastTaskStateChange(this)
		}

	/**
	 * The total amount of items (or null if unknown)
	 * @since 1.5.0
	 */
	var totalItems
		get() = _totalItems
		set(value) {
			_totalItems = value
			manager.broadcastTaskStateChange(this)
		}

	/**
	 * The task's current subtask
	 * @since 1.5.0
	 */
	var subtask
		get() = _subtask
		set(value) {
			_subtask = value
			manager.broadcastTaskStateChange(this)
		}

	/**
	 * Whether the task succeeded
	 * @since 1.5.0
	 */
	val succeeded
		get() = _succeeded

	/**
	 * Whether the task is currently being cancelled
	 * @since 1.5.0
	 */
	var cancelling
		get() = _cancelling
		set(value) {
			_cancelling = value
			manager.broadcastTaskStateChange(this)
		}

	/**
	 * Whether the task has been cancelled
	 * @since 1.5.0
	 */
	val cancelled
		get() = _cancelled

	/**
	 * Whether the task failed
	 * @since 1.5.0
	 */
	val failed
		get() = _failed

	/**
	 * The current account IDs that should be able to view the task and receive events for it
	 * @since 1.5.0
	 */
	val subjects
		get() = _subjects.toTypedArray()

	/**
	 * Creates a JSON representation of the task, including all of its constants and variables, in its current state
	 * @return A JSON representation of the task
	 * @since 1.5.0
	 */
	fun toJson() = json {obj(
			"id" to id,
			"name" to name,
			"cancellable" to isCancellable,
			"cancel_permission" to cancelPermission,
			"global" to isGlobal,
			"view_permission" to viewPermission,
			"progress_type" to progressType.name,
			"created_on" to createdOn.toString(),
			"finished_items" to _finishedItems,
			"total_items" to _totalItems,
			"subtask" to _subtask,
			"succeeded" to _succeeded,
			"cancelling" to _cancelling,
			"cancelled" to _cancelled,
			"failed" to _failed
	)}

	/**
	 * Creates a JSON representation of the task's state (its variable values)
	 * @return A JSON representation of the task's state (its variable values)
	 * @since 1.5.0
	 */
	fun stateToJson() = json {obj(
			"finished_items" to finishedItems,
			"total_items" to totalItems,
			"subtask" to _subtask,
			"succeeded" to _succeeded,
			"cancelling" to _cancelling,
			"cancelled" to _cancelled,
			"failed" to _failed
	)}

	/**
	 * Adds an account to the list of subjects that will receive events for the task
	 * @param id The account ID of the new subject
	 * @since 1.5.0
	 */
	fun addSubject(id: Int) {
		// Only add subject if not already present
		if(!_subjects.contains(id)) {
			_subjects.add(id)
			manager.broadcastTaskCreatedTo(this, id)
		}
	}

	/**
	 * Removes an account from the list of subjects that will receive events for the task
	 * @param id The account ID of the subject to remove
	 * @since 1.5.0
	 */
	fun removeSubject(id: Int) {
		// Only remove subject if already present
		if(_subjects.contains(id)) {
			_subjects.remove(id)
			manager.broadcastTaskDeletedTo(this, id)
		}
	}

	/**
	 * Sets the task as succeeded, runs success handlers, and removes the task from the TaskManager instance's current tasks
	 * @since 1.5.0
	 */
	fun succeed() {
		_succeeded = true
		manager.broadcastTaskStateChange(this)
		manager.removeTask(id)
		for(hdlr in successHandlers)
			hdlr.handle(null)
	}

	/**
	 * Cancels and deletes the task (only has an effect if isCancellable is true)
	 * @since 1.5.0
	 */
	fun cancel() {
		_cancelled = true
		manager.broadcastTaskStateChange(this)
		manager.removeTask(id)
		for(hdlr in cancelHandlers)
			hdlr.handle(null)
	}

	/**
	 * Sets the task as failed, runs failure handlers, and removes the task from the TaskManager instance's current tasks
	 * @param cause The cause of the failure, or null if not caused by a throwable
	 * @since 1.5.0
	 */
	fun fail(cause: Throwable? = null) {
		_failed = true
		manager.broadcastTaskStateChange(this)
		manager.removeTask(id)
		for(hdlr in failureHandlers)
			hdlr.handle(cause)
	}

	/**
	 * Requests a cancellation by running the cancel request handler (only has an effect if isCancellable is true)
	 * @since 1.5.0
	 */
	fun requestCancellation() {
		if(isCancellable && !cancelling && cancelRequestHandler != null) {
			cancelling = true
			cancelRequestHandler.handle(null)
		}
	}
}