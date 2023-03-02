package net.termer.twinemedia.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.time.OffsetDateTime

/**
 * A Jackson Databind module that serializes a minimal set of time-related data types
 * @since 2.0.0
 */
class MinimalTimeModule : SimpleModule() {
    init {
        addSerializer(OffsetDateTime::class.java, OffsetDateTimeSerializer())
    }

    private class OffsetDateTimeSerializer(t: Class<OffsetDateTime>? = null) : StdSerializer<OffsetDateTime>(t) {
        override fun serialize(date: OffsetDateTime, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(date.toString())
        }
    }
}
