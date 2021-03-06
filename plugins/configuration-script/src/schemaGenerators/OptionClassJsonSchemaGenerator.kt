package com.intellij.configurationScript.schemaGenerators

import com.intellij.configurationScript.LOG
import com.intellij.configurationStore.Property
import com.intellij.openapi.components.BaseState
import com.intellij.serialization.stateProperties.CollectionStoredProperty
import com.intellij.serialization.stateProperties.EnumStoredProperty
import com.intellij.serialization.stateProperties.MapStoredProperty
import com.intellij.util.ReflectionUtil
import gnu.trove.THashMap
import org.jetbrains.io.JsonObjectBuilder
import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

internal class OptionClassJsonSchemaGenerator(val definitionNodeKey: String) {
  val definitionPointerPrefix = "#/$definitionNodeKey/"
  private val queue: MutableSet<Class<out BaseState>> = hashSetOf()

  private val definitionBuilder = StringBuilder()
  val definitions = JsonObjectBuilder(definitionBuilder, indentLevel = 1)

  fun describe(): CharSequence {
    if (queue.isEmpty()) {
      return definitionBuilder
    }

    val list: MutableList<Class<out BaseState>> = arrayListOf()
    while (true) {
      if (queue.isEmpty()) {
        return definitionBuilder
      }

      list.clear()
      list.addAll(queue)
      queue.clear()
      list.sortedBy { it.name }

      for (clazz in list) {
        definitions.map(clazz.name.replace('.', '_')) {
          "type" to "object"
          map("properties") {
            val instance = ReflectionUtil.newInstance(clazz)
            buildJsonSchema(instance, this, this@OptionClassJsonSchemaGenerator)
          }
          "additionalProperties" to false
        }
      }
    }
  }

  fun addClass(clazz: Class<out BaseState>): CharSequence {
    queue.add(clazz)
    return clazz.name.replace('.', '_')
  }
}

internal fun buildJsonSchema(state: BaseState,
                             builder: JsonObjectBuilder,
                             subObjectSchemaGenerator: OptionClassJsonSchemaGenerator?,
                             customFilter: ((name: String) -> Boolean)? = null) {
  val properties = state.__getProperties()
  val memberProperties = state::class.memberProperties
  var propertyToAnnotation: MutableMap<String, Property>? = null
  for (property in memberProperties) {
    val annotation = property.findAnnotation<Property>() ?: continue
    if (propertyToAnnotation == null) {
      propertyToAnnotation = THashMap()
    }
    propertyToAnnotation.put(property.name, annotation)
  }

  for (property in properties) {
    val name = property.name!!
    val annotation = propertyToAnnotation?.get(name)
    if (annotation?.ignore == true) {
      continue
    }

    if (customFilter != null && !customFilter(name)) {
      continue
    }

    builder.map(name) {
      "type" to property.jsonType.jsonName

      annotation?.let {
        if (it.description.isNotEmpty()) {
          "description" toUnescaped it.description
        }
      }

      when (property) {
        is EnumStoredProperty<*> -> describeEnum(property)
        is MapStoredProperty<*, *> -> {
          map("additionalProperties") {
            "type" to "string"
          }
        }
        is CollectionStoredProperty<*, *> -> {
          val propertyInfo = state.javaClass.kotlin.members.first { it.name == property.name }
          val type = propertyInfo.returnType.javaType
          if (type !is ParameterizedType) {
            LOG.error("$type not supported for collection property $propertyInfo")
          }
          else {
            val actualTypeArguments = type.actualTypeArguments
            LOG.assertTrue(actualTypeArguments.size == 1)
            val listType = actualTypeArguments[0]

            when {
              listType === java.lang.String::class.java -> {
                map("items") {
                  "type" to "string"
                }
              }
              subObjectSchemaGenerator == null -> {
                LOG.error("$type not supported for collection property $propertyInfo because subObjectSchemaGenerator is not specified")
              }
              else -> {
                map("items") {
                  @Suppress("UNCHECKED_CAST")
                  definitionReference(subObjectSchemaGenerator.definitionPointerPrefix,
                                      subObjectSchemaGenerator.addClass(listType as Class<out BaseState>))
                }
              }
            }
          }
        }
      }

      // todo object definition
    }
  }
}

private fun JsonObjectBuilder.describeEnum(property: EnumStoredProperty<*>) {
  rawArray("enum") { stringBuilder ->
    val enumConstants = property.clazz.enumConstants
    for (enum in enumConstants) {
      stringBuilder.append('"').append(enum.toString().toLowerCase()).append('"')
      if (enum !== enumConstants.last()) {
        stringBuilder.append(',')
      }
    }
  }
}
