package net.termer.twinemedia.dataobject

import io.vertx.kotlin.core.json.jsonObjectOf
import net.termer.twinemedia.util.JsonSerializable

/**
 * DTO for a single result.
 * Should be used to wrap responses that only contain one record.
 * @since 2.0.0
 */
data class SingleResultDto<T : JsonSerializable>(
    val result: T
) : JsonSerializable() {
    override fun toJson() = jsonObjectOf("result" to result.toJson())
}
