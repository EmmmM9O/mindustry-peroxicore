package peroxicore.graphics

import arc.files.Fi
import arc.util.ArcRuntimeException
import mindustry.Vars
import peroxicore.files.*

interface ShaderProcessor {
  fun process(source: String, fragment: Boolean): String
}

open class MacroProcessor(vararg val macros: Pair<String, String?>) : ShaderProcessor {
  override fun process(source: String, fragment: Boolean): String =
    macros
      .joinToString("\n") { (key, value) -> "#define $key ${value ?: ""}" }
      .takeIf { it.isNotEmpty() }
      ?.let { "$it\n\n$source" } ?: source
}

open class IncludeProcessor(
  private val includePattern: Regex = defaultRegex,
  private val maxIncludeDepth: Int = 50,
) : ShaderProcessor {
  companion object {
    val defaultRegex =
      """^\s*#include\s+["<]([^">]+)[">]\s*(?://.*)?$""".toRegex(RegexOption.MULTILINE)
  }

  override fun process(source: String, fragment: Boolean): String {
    val path = POShader.getShaderFile(source)
    val file = POShaders.findFi(path)
    return processIncludes(source, file)
  }

  open fun getInclude(name: String, base: Fi): Fi? =
    when {
      name.startsWith("/") -> Vars.tree.get("shaders$name")
      else -> base.parent()?.sub(name)
    }

  private fun processIncludes(source: String, path: Fi, depth: Int = 0): String {
    require(depth <= maxIncludeDepth) { "Include depth exceeded: $maxIncludeDepth at $path" }

    return includePattern.replace(source) { match ->
      val includeName = match.groupValues[1]
      val file =
        (getInclude(includeName, path)
            ?: throw ArcRuntimeException("Include not found: $includeName from $path"))
          .also {
            require(it.exists()) { "File not found: $it (included as $includeName from $path)" }
          }

      processIncludes(file.readString(), file, depth + 1)
    }
  }
}

@DslMarker annotation class ProcessorDsl

@ProcessorDsl
class ProcessorsBuilder {
  val processors = mutableListOf<ShaderProcessor>()
  var default = true

  fun clearDefaults() {
    default = false
  }

  fun macro(vararg entries: Any) {
    processors.add(
      MacroProcessor(
        *entries
          .map {
            when (it) {
              is String -> it to null
              is Pair<*, *> -> it.first.toString() to it.second?.toString()
              else -> it.toString() to null
            }
          }
          .toTypedArray()
      )
    )
  }

  fun includes(regex: Regex = IncludeProcessor.defaultRegex, maxIncludeDepth: Int = 50) {
    processors.add(IncludeProcessor(regex, maxIncludeDepth))
  }

  fun build(): List<ShaderProcessor> =
    if (default) processors + POShaders.defaultProcessors else processors
}

inline fun shaderProcessors(builder: ProcessorsBuilder.() -> Unit): List<ShaderProcessor> =
  ProcessorsBuilder().apply(builder).build()

fun Array<out ShaderProcessor>.process(source: String, fragment: Boolean) =
  this.fold(source) { acc, p -> p.process(acc, fragment) }
