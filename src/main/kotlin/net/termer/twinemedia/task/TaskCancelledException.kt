package net.termer.twinemedia.task

/**
 * Exception to be thrown when a task is cancelled
 * @author termer
 * @since 1.5.0
 */
class TaskCancelledException(msg: String): TaskException(msg)