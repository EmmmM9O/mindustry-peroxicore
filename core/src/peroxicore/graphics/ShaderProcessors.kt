package peroxicore.graphics

import arc.files.Fi

/**
 * State handed to every [ShaderProcessor] of a single pass.
 *
 * The source file travels with the text, so processors never have to recover it from the shader itself.
 */
class ShaderContext(
  /** Whether this is the fragment (as opposed to vertex) stage. */
  val fragment: Boolean,
  /** File the source was read from, or `null` if it did not come from disk. */
  val file: Fi?,
  /** Macros the pass starts with; [Preprocessor] writes the final macro table back into it. */
  val defines: MutableMap<String, String?> = LinkedHashMap(),
  /** Scratch space for processors that need to pass data to each other. */
  val vars: MutableMap<String, Any?> = LinkedHashMap(),
) {
  val stage: String
    get() = if (fragment) "fragment" else "vertex"
}

fun interface ShaderProcessor {
  fun process(source: String, context: ShaderContext): String
}

/**
 * Declares macros for the rest of the pipeline.
 *
 * When a [Preprocessor] is part of the same pipeline it emits the `#define` header itself, so the
 * macros do not shift the shader source's line numbers; [emitText] is then false. Without one, the
 * header is prepended directly.
 */
open class MacroProcessor(
  vararg val macros: Pair<String, String?>,
  private val emitText: Boolean = true,
) : ShaderProcessor {

  init {
    require(macros.isNotEmpty()) { "MacroProcessor needs at least one macro" }
  }

  override fun process(source: String, context: ShaderContext): String {
    macros.forEach { (key, value) -> context.defines[key] = value }
    if (!emitText) return source
    return macros.joinToString("\n") { (key, value) -> "#define $key ${value ?: ""}" } +
      "\n" + source
  }
}

/** Normalizes the loose macro entry forms accepted by the DSL into a name/value pair. */
internal fun Any.asMacro(): Pair<String, String?> =
  when (this) {
    is Pair<*, *> -> first.toString() to second?.toString()
    is Map.Entry<*, *> -> key.toString() to value?.toString()
    else -> toString() to null
  }

@DslMarker annotation class ProcessorDsl

@ProcessorDsl
class ProcessorsBuilder {
  val processors = mutableListOf<ShaderProcessor>()

  /** Set to false to skip [POShaders.defaultProcessors] entirely. */
  var default = true

  fun clearDefaults() {
    default = false
  }

  /** Appends an arbitrary stage. */
  fun raw(processor: ShaderProcessor) {
    processors += processor
  }

  /** Defines macros; strings are emitted as bare defines, pairs as `name to value`. */
  fun macro(vararg entries: Any) {
    processors +=
      MacroProcessor(*entries.map { it.asMacro() }.toTypedArray())
  }

  /** Alias of [macro]. */
  fun define(vararg entries: Any) = macro(*entries)

  /**
   * Adds a [Preprocessor].
   *
   * Note that this replaces the default one: [build] drops a default processor when the caller
   * already supplied one of the same class, so includes are never expanded twice.
   */
  fun preprocessor(
    conditionals: Boolean = true,
    includePaths: List<String> = emptyList(),
    maxIncludeDepth: Int = 50,
    lineDirectives: Boolean = true,
    vararg defines: Pair<String, String?>,
  ) {
    processors +=
      Preprocessor(
        conditionals = conditionals,
        maxIncludeDepth = maxIncludeDepth,
        lineDirectives = lineDirectives,
        includePaths = includePaths,
        defines = defines.toMap(),
      )
  }

  /** Includes only: conditional directives are passed through untouched. */
  fun includes(
    includePaths: List<String> = emptyList(),
    maxIncludeDepth: Int = 50,
    lineDirectives: Boolean = true,
  ) = preprocessor(
    conditionals = false,
    includePaths = includePaths,
    maxIncludeDepth = maxIncludeDepth,
    lineDirectives = lineDirectives,
  )

  fun build(): List<ShaderProcessor> {
    val all =
      if (default) processors + POShaders.defaultProcessors.filterNot { def -> processors.any { it::class == def::class } }
      else processors

    if (all.none { it is Preprocessor }) return all

    // The preprocessor writes the macro header, so macros must both run first and leave the text
    // alone; otherwise a `#define` would shift every reported line number by one.
    val macros = all.filterIsInstance<MacroProcessor>()
      .map { MacroProcessor(*it.macros, emitText = false) }
    val rest = all.filterNot { it is MacroProcessor }
    return macros + rest
  }
}

inline fun shaderProcessors(builder: ProcessorsBuilder.() -> Unit): List<ShaderProcessor> =
  ProcessorsBuilder().apply(builder).build()

fun List<ShaderProcessor>.process(source: String, context: ShaderContext): String =
  fold(source) { acc, processor -> processor.process(acc, context) }

fun Array<out ShaderProcessor>.process(source: String, context: ShaderContext): String =
  asList().process(source, context)
