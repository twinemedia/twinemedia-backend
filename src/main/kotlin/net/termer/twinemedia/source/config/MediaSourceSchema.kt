package net.termer.twinemedia.source.config

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

/**
 * Configuration schema for media source configs
 * @author termer
 * @since 1.5.0
 */
class MediaSourceSchema(
		/**
		 * The schema's sections. These exist purely for form generation, and don't have any effect on how data is handled.
		 * @since 1.5.0
		 */
		val sections: Array<Section>,
		/**
		 * The schema's fields
		 * @since 1.5.0
		 */
		val fields: Array<Field>
) {
	/**
	 * Returns a JSON representation of this schema
	 * @return A JSON representation of this schema
	 * @since 1.5.0
	 */
	fun toJson() = json {obj(
			"sections" to JsonObject().apply {
				for(section in sections)
					put(section.sectionName, json {obj(
							"name" to section.name
					)})
			},
			"fields" to JsonObject().apply {
				for(field in fields)
					put(field.fieldName, json {obj(
							"name" to field.name,
							"type" to field.type.name,
							"optional" to field.optional,
							"default" to field.default,
							"section" to field.section
					)})
			}
	)}

	/**
	 * Returns whether the provided value matches the specified type
	 * @param value The value to check
	 * @param type The type to check the value against
	 * @return Whether the provided value matches the specified type
	 * @since 1.5.0
	 */
	private fun isType(value: Any?, type: Field.Type) = when {
		value == null -> {
			false
		}
		type == Field.Type.DATE -> {
			value is String && value.matches(Regex("\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\.\\d+([+-][0-2]\\d:[0-5]\\d|Z)"))
		}
		type == Field.Type.NUMBER -> {
			value is Byte || value is Short || value is Int || value is Long || value is Float || value is Double
		}
		else -> {
			val required = when(type) {
				Field.Type.ARRAY -> JsonArray::class.java
				Field.Type.BOOLEAN -> java.lang.Boolean::class.java
				Field.Type.OBJECT -> JsonObject::class.java
				else -> String::class.java
			}

			required.isAssignableFrom(value::class.java)
		}
	}

	/**
	 * Validates the provided JSON against this schema, and returns either a successful validation or errors about the JSON
	 * @param json The JSON to validate
	 * @return The validation response
	 * @since 1.5.0
	 */
	fun validate(json: JsonObject): ValidationResponse {
		// Check that all required fields are present
		for(field in fields)
			if(!field.optional && !json.containsKey(field.fieldName))
				return ValidationResponse(errorType = "MISSING_FIELD", errorText = "Missing field \"${field.fieldName}\" (${field.name})")
			else if(!isType(json.getValue(field.fieldName), field.type))
				return ValidationResponse(errorType = "INVALID_TYPE", errorText = "Field \"${field.fieldName}\" (${field.name}) is not type \"${field.type}\"")

		// If it got this far, everything is fine, validation succeeded
		return ValidationResponse(valid = true)
	}

	/**
	 * Class that holds information about schema validation responses
	 * @author termer
	 * @since 1.5.0
	 */
	class ValidationResponse(
			/**
			 * Whether this is a valid response
			 * @since 1.5.0
			 */
			val valid: Boolean = false,
			/**
			 * The type of error in this response
			 * @since 1.5.0
			 */
			val errorType: String? = null,
			/**
			 * The human-readable error in this response
			 * @since 1.5.0
			 */
			val errorText: String? = null
	)

	/**
	 * Class that contains info about a schema section
	 * @author termer
	 * @since 1.5.0
	 */
	class Section(
			/**
			 * The section's internal name
			 * @since 1.5.0
			 */
			val sectionName: String,
			/**
			 * The section's human-readable name
			 * @since 1.5.0
			 */
			val name: String
	)

	/**
	 * Class that contains info about a schema field
	 * @author termer
	 * @since 1.5.0
	 */
	class Field(
			/**
			 * The field's internal name
			 * @since 1.5.0
			 */
			val fieldName: String,
			/**
			 * The field's human-readable name
			 * @since 1.5.0
			 */
			val name: String,
			/**
			 * The field's required data type
			 * @since 1.5.0
			 */
			val type: Type,
			/**
			 * Whether the field is optional
			 * @since 1.5.0
			 */
			val optional: Boolean,
			/**
			 * The field's default value if none is provided (must be a JSON-safe type)
			 * @since 1.5.0
			 */
			val default: Any?,
			/**
			 * The section this field belongs to
			 * @since 1.5.0
			 */
			val section: String
	) {
		/**
		 * Possible data types for fields
		 * @author termer
		 * @since 1.5.0
		 */
		enum class Type {
			ARRAY,
			BOOLEAN,
			DATE,
			NUMBER,
			OBJECT,
			STRING
		}
	}
}