package net.termer.twinemedia.middleware

import net.termer.krestx.api.util.SuspendRequestHandler

/**
 * Interface to be implemented by all middleware
 * @since 2.0.0
 */
interface Middleware : SuspendRequestHandler
