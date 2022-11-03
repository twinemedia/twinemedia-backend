package net.termer.twinemedia.task

import io.vertx.core.Handler
import io.vertx.kotlin.core.json.jsonObjectOf
import kotlinx.coroutines.DelicateCoroutinesApi
import net.termer.twinemedia.dataobject.AccountRow
import java.util.concurrent.ConcurrentHashMap

/**
 * Class that manages current tasks and events from them that are sent to users
 * @author termer
 * @since 1.5.0
 */
@DelicateCoroutinesApi
class TaskManager {
	private var taskIdIncrementer = 0
	private val _tasks = ConcurrentHashMap<Int, Task>()

	// Returns a new unique task ID integer
	private fun newTaskId() = taskIdIncrementer++

	/**
	 * All current tasks
	 * @since 1.5.0
	 */
	val tasks
		get() = _tasks.values.toTypedArray()

	/**
	 * Returns the task with the specified ID, or null if it does not exist
	 * @param id The task ID
	 * @return The task with the specified ID, or null if it does not exist
	 * @since 1.5.0
	 */
	fun getTask(id: Int) = _tasks[id]

//	/**
//	 * Creates a new task, broadcasts its creation, and returns it
//	 * @param name The task's name
//	 * @param isGlobal Whether the task is viewable by everyone (minus those without appropriate permissions)
//	 * @param progressType How progress should be displayed
//	 * @param viewPermission The permission required to view the task (null if none is required, does not apply to explicitly added subjects)
//	 * @param initialSubjects The task's initial subjects (account IDs that will receive events for and can view the task)
//	 * @param cancelRequestHandler Handler to be run when a cancellation request is issued (null if event will not be cancellable)
//	 * @param cancelPermission The permission required to cancel the task (null if task is not cancellable)
//	 * @return The newly created task
//	 * @since 1.5.0
//	 */
//	fun createTask(name: String, isGlobal: Boolean, progressType: Task.ProgressType, viewPermission: String? = null, initialSubjects: Array<Int> = emptyArray(), cancelRequestHandler: Handler<Void>? = null, cancelPermission: String? = null): Task {
//		// Create new task
//		val task = Task(
//			manager = this,
//			id = newTaskId(),
//			name = name,
//			isGlobal = isGlobal,
//			progressType = progressType,
//			viewPermission = viewPermission,
//			initialSubjects = initialSubjects,
//			isCancellable = cancelRequestHandler != null,
//			cancelRequestHandler = cancelRequestHandler,
//			cancelPermission = cancelPermission
//		)
//
//		// Add it to tasks
//		_tasks[task.id] = task
//
//		// Broadcast creation
//		broadcastTaskCreated(task)
//
//		return task
//	}

	/**
	 * Returns whether the provided account can view the specified task
	 * @param account The account
	 * @param task The task to check against
	 * @return Whether the provided account can view the specified task
	 * @since 1.5.0
	 */
	fun canAccountViewTask(account: AccountRow, task: Task): Boolean {
		return task.subjects.contains(account.internalId) || (task.isGlobal && (task.viewPermission == null || account.hasPermission(task.viewPermission)))
	}

	/**
	 * Returns all tasks that are visible to the provided account
	 * @param account The account to check against
	 * @return All tasks that are visible to the provided account
	 * @since 1.5.0
	 */
	fun tasksViewableByAccount(account: AccountRow): Array<Task> {
		val tasks = tasks
		val res = ArrayList<Task>()

		for(task in tasks)
			if(canAccountViewTask(account, task))
				res.add(task)

		return res.toTypedArray()
	}

	/**
	 * Returns whether the provided account can cancel the specified task
	 * @param account The account
	 * @param task The task to check against
	 * @return Whether the provided account can cancel the specified task
	 * @since 1.5.0
	 */
	fun canAccountCancelTask(account: AccountRow, task: Task): Boolean {
		return task.isCancellable && canAccountViewTask(account, task) && (task.cancelPermission == null || account.hasPermission(task.cancelPermission))
	}

//	/**
//	 * Returns all SockJSClient instances that can view the provided task
//	 * @param task The task to check against
//	 * @return All SockJSClient instances that can view the provided task
//	 * @since 1.5.0
//	 */
//	fun clientsThatCanViewTask(task: Task): Array<SockJSClient> {
//		val clients = sockJSManager.getClients()
//		val res = ArrayList<SockJSClient>()
//
//		for(client in clients) {
//			if(canAccountViewTask(client.account, task))
//				res.add(client)
//		}
//
//		return res.toTypedArray()
//	}

//	/**
//	 * Broadcasts a task creation to all clients that can view it
//	 * @param task The task
//	 * @since 1.5.0
//	 */
//	fun broadcastTaskCreated(task: Task) {
//		val clients = clientsThatCanViewTask(task)
//
//		for(client in clients)
//			sockJSManager.broadcastEventByAccountId(client.account.id, "task_create", task.toJson())
//	}

//	/**
//	 * Broadcasts a task creation to all clients with the specified account ID
//	 * @param task The task
//	 * @param accountId The account ID
//	 * @since 1.5.0
//	 */
//	fun broadcastTaskCreatedTo(task: Task, accountId: Int) {
//		sockJSManager.broadcastEventByAccountId(accountId, "task_create", task.toJson())
//	}

//	/**
//	 * Broadcasts a task's state to all clients that can view it
//	 * @param task The task
//	 * @since 1.5.0
//	 */
//	fun broadcastTaskStateChange(task: Task) {
//		val clients = clientsThatCanViewTask(task)
//
//		for(client in clients)
//			sockJSManager.broadcastEventByAccountId(client.account.id, "task_update", jsonObjectOf(
//				"id" to task.id,
//				"state" to task.stateToJson()
//			))
//	}

//	/**
//	 * Broadcasts a task deletion to all clients that can view it
//	 * @param task The task
//	 * @since 1.5.0
//	 */
//	fun broadcastTaskDeleted(task: Task) {
//		val clients = clientsThatCanViewTask(task)
//
//		for(client in clients)
//			sockJSManager.broadcastEventByAccountId(client.account.id, "task_delete", jsonObjectOf(
//				"id" to task.id
//			))
//	}

//	/**
//	 * Broadcasts a task deletion to all clients with the specified account ID
//	 * @param task The task
//	 * @param accountId The account ID
//	 * @since 1.5.0
//	 */
//	fun broadcastTaskDeletedTo(task: Task, accountId: Int) {
//		sockJSManager.broadcastEventByAccountId(accountId, "task_delete", jsonObjectOf(
//			"id" to task.id
//		))
//	}

//	/**
//	 * Removes a task from the current tasks list by its ID
//	 * @param id The task's ID
//	 * @param sendDelete Whether to send a delete event to all subjects that have permission to view the task
//	 * @since 1.5.0
//	 */
//	fun removeTask(id: Int, sendDelete: Boolean = false) {
//		val task = _tasks[id]
//
//		if(task != null) {
//			_tasks.remove(id)
//			if(sendDelete)
//				broadcastTaskDeleted(task)
//		}
//	}
}