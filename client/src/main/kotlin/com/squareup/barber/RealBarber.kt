package com.squareup.barber

import com.squareup.barber.models.CopyModel
import com.squareup.barber.models.DocumentCopy
import com.squareup.barber.models.DocumentSpec
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

internal class RealBarber(val installedDocumentCopy: Map<KClass<out CopyModel>, DocumentCopy>) : Barber {
  override fun <C : CopyModel, D : DocumentSpec> newRenderer(
    copyModelClass: KClass<out C>,
    documentSpecClass: KClass<out D>
  ): Renderer<C, D> {
    // Lookup installed DocumentCopy that corresponds to CopyModel
    val documentCopy: DocumentCopy = installedDocumentCopy[copyModelClass] ?: throw BarberException(
      problems = listOf("""
      |Attempted to render with DocumentCopy that has not been installed for CopyModel: $copyModelClass.
    """.trimMargin()))

    // Confirm that output DocumentSpec is a valid target for the DocumentCopy
    if (!documentCopy.targets.contains(documentSpecClass)) {
      throw BarberException(problems = listOf("""
        |Specified target $documentSpecClass not a valid target for CopyModel's corresponding DocumentCopy.
        |Valid targets:
        |${documentCopy.targets}
      """.trimMargin()))
    }

    // Pull out required parameters from DocumentSpec constructor
    val documentSpecConstructor = documentSpecClass.primaryConstructor!!
    val documentSpecParametersByName = documentSpecConstructor.parameters.associateBy { it.name }

    // Find missing fields in DocumentCopy
    // Missing fields occur when a nullable field in DocumentSpec is not an included key in the DocumentCopy fields
    // In the Parameters Map in the DocumentSpec constructor though, all parameter keys must be present (including
    // nullable)
    val missingFields = documentSpecParametersByName.filterKeys {
      !it.isNullOrBlank() && !documentCopy.fields.containsKey(it)
    }

    // Initialize keys for missing fields in DocumentCopy
    val documentCopyFields: MutableMap<String, String?> = documentCopy.fields.toMutableMap()
    missingFields.map { documentCopyFields.putIfAbsent(it.key!!, null) }

    return RealRenderer(documentSpecConstructor, documentSpecParametersByName, documentCopyFields)
  }

  override fun getAllRenderers(): LinkedHashMap<RendererKey, Renderer<*, *>> {
    val renderers: LinkedHashMap<RendererKey, Renderer<*,*>> = linkedMapOf()
    for (entry in installedDocumentCopy) {
      val documentCopy = entry.value
      documentCopy.targets.forEach { renderers[RendererKey(documentCopy.source, it)] = newRenderer(documentCopy.source, it) }
    }
    return renderers
  }
}
