package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import net.termer.twinemedia.Module.Companion.taskManager
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.ServerManager.*
import net.termer.twinemedia.util.*
import net.termer.vertx.kotlin.validation.RequestValidator
import net.termer.vertx.kotlin.validation.validator.*

/**
 * Sets up all routes for getting info about tasks and interacting with them
 * @since 1.5.0
 */
@DelicateCoroutinesApi
fun tasksController() {
	for(hostname in appHostnames()) {
		// Returns tasks visible to the user
		router().get("/api/v1/tasks").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectRoute()) {
					val tasks = taskManager.tasksViewableByAccount(r.account())

					val arr = JsonArray()
					for(task in tasks)
						arr.add(task.toJson())

					// Send tasks
					r.success(json {obj(
							"tasks" to arr
					)})
				}
			}
		}

		// Returns info about a task
		// Route parameters:
		//  - id: Int, the task's ID
		router().get("/api/v1/task/:id").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectRoute()) {
					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())

					if(v.validate(r)) {
						val id = v.parsedRouteParam("id") as Int

						// Fetch task
						val task = taskManager.getTask(id)

						if(task == null || !taskManager.canAccountViewTask(r.account(), task))
							r.error("Invalid ID")
						else
							r.success(task.toJson())
					} else {
						r.error(v)
					}
				}
			}
		}

		router().post("/api/v1/task/:id/cancel").virtualHost(hostname).handler { r ->
			GlobalScope.launch(vertx().dispatcher()) {
				if(r.protectRoute()) {
					// Request validation
					val v = RequestValidator()
							.routeParam("id", IntValidator())

					if(v.validate(r)) {
						val id = v.parsedRouteParam("id") as Int

						// Fetch task
						val task = taskManager.getTask(id)

						if(task == null || !taskManager.canAccountViewTask(r.account(), task)) {
							r.error("Invalid ID")
							return@launch
						}

						// Check if task is cancellable
						if(!task.isCancellable) {
							r.error("Task is not cancellable")
							return@launch
						}

						// Check if user has permission to cancel task
						if(!taskManager.canAccountCancelTask(r.account(), task)) {
							r.error("You do not have permission to cancel this task")
							return@launch
						}

						// Request task cancellation if nothing is stopping it
						task.requestCancellation()

						r.success()
					} else {
						r.error(v)
					}
				}
			}
		}
	}
}