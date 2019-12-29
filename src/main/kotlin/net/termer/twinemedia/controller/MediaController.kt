package net.termer.twinemedia.controller

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.termer.twine.Twine
import net.termer.twine.ServerManager.get
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.fetchMediaList
import net.termer.twinemedia.util.error
import net.termer.twinemedia.util.protectWithPermission
import net.termer.twinemedia.util.success
import java.lang.NumberFormatException

/**
 * Sets up all routes for retrieving and modifying file info + processing files
 * @since 1.0
 */
fun mediaController() {
    val domain = Twine.domains().byName(config.domain).domain()

    get("/api/v1/media", domain) { r ->
        val params = r.request().params()
        GlobalScope.launch(vertx().dispatcher()) {
            if(r.protectWithPermission("files.list")) {
                try {
                    // Collect index and limit parameters
                    val offset = (if (params.contains("offset")) params.get("offset").toInt() else 0).coerceAtLeast(0)
                    val limit = (if(params.contains("limit")) params.get("limit").toInt() else 100).coerceAtMost(100)

                    // Fetch files
                    val media = fetchMediaList(offset, limit)

                    // Create JSON array of files
                    val arr = JsonArray()

                    for(file in media?.rows.orEmpty())
                        arr.add(file)

                    // Send files
                    r.success(JsonObject().put("media", arr))
                } catch(e : NumberFormatException) {
                    r.error("`index` and `limit` parameters must be numbers")
                }
            }
        }
    }
}