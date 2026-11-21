package peroxicore.graphics

import arc.Core
import arc.files.Fi
import arc.graphics.Color
import arc.graphics.gl.Shader
import arc.math.Mat
import arc.math.geom.Vec2
import arc.math.geom.Vec3
import arc.util.ArcRuntimeException
import arc.util.Log
import mindustry.Vars

object POShaders {
  const val SHADER_ROOT = "shaders"

  /** Macros injected into every shader, and visible to `#ifdef` in the [Preprocessor]. */
  val macros = LinkedHashMap<String, String?>()

  /** Extra directories searched by `#include`, relative to the `shaders/` root. */
  val includePaths = mutableListOf<String>()

  /** Write every processed source to `data/peroxicore/shaders/` before compiling. */
  var dumpShaders = false

  val defaultProcessors = mutableListOf<ShaderProcessor>(Preprocessor())

  private val files = HashMap<String, Fi>()

  fun findFi(file: String): Fi =
    findFiOrNull(file)
      ?: throw ArcRuntimeException("Shader file not found: $SHADER_ROOT/$file")

  fun findFiOrNull(file: String): Fi? {
    val key = file.removePrefix("/")
    files[key]?.let { return it }
    if (Vars.headless) return null
    val fi = Vars.tree.get("$SHADER_ROOT/$key")
    if (!fi.exists()) return null
    files[key] = fi
    return fi
  }

  /** Clears the `Fi` cache; call after the mod file tree changes. */
  fun clearCache() {
    files.clear()
  }

  private val factories = LinkedHashMap<String, () -> POShader>()
  private val instances = LinkedHashMap<String, POShader>()

  /** Registers a lazily built shader; the factory runs on first [get]/[reload]. */
  fun register(name: String, factory: () -> POShader) {
    instances.remove(name)?.dispose()
    factories[name] = factory
  }

  fun registered(): Set<String> = factories.keys

  fun getOrNull(name: String): POShader? = factories[name]?.let { get(name) }

  fun get(name: String): POShader =
    instances.getOrPut(name) {
      factories[name]?.invoke()
        ?: throw ArcRuntimeException("Shader not registered: $name (known: ${factories.keys})")
    }

  /** Rebuilds a registered shader, disposing the previous program once the new one compiled. */
  fun reload(name: String): POShader? {
    val factory = factories[name] ?: return null
    val fresh = factory()
    instances.put(name, fresh)?.takeIf { it !== fresh }?.dispose()
    return fresh
  }

  fun reloadAll() {
    factories.keys.toList().forEach(::reload)
  }

  fun disposeAll() {
    instances.values.forEach { it.dispose() }
    instances.clear()
  }
}

/** Final GLSL handed to the driver for both stages, together with the files they came from. */
class POShaderSource(
  val vertFi: Fi?,
  val fragFi: Fi?,
  val vert: String,
  val frag: String,
) {
  companion object {
    fun of(
      vert: String,
      frag: String,
      processors: Array<out ShaderProcessor> = POShaders.defaultProcessors.toTypedArray(),
    ): POShaderSource {
      val vertFi = POShaders.findFi("$vert.vert")
      val fragFi = POShaders.findFi("$frag.frag")
      return POShaderSource(
        vertFi,
        fragFi,
        render(vertFi, "$vert.vert", false, processors),
        render(fragFi, "$frag.frag", true, processors),
      )
    }

    fun of(
      vert: String,
      frag: String,
      builder: ProcessorsBuilder.() -> Unit,
    ) = of(vert, frag, shaderProcessors(builder).toTypedArray())

    private fun render(
      fi: Fi,
      name: String,
      fragment: Boolean,
      processors: Array<out ShaderProcessor>,
    ): String {
      // The shader file name is exposed as a define *and* a macro, so it is visible to `#ifdef`
      // without being written into the text (where it would shift reported line numbers).
      val context = ShaderContext(fragment, fi, linkedMapOf(POShader.SHADERFILE_MACRO to name))
      val processed = processors.process(fi.readString(), context)
      // preprocessGLSL hoists any explicit `#version` back to line 1.
      val final = preprocessGLSL(processed, fragment, name)

      if (POShaders.dumpShaders) {
        runCatching {
          Vars.dataDirectory.child("peroxicore/shaders/$name").writeString(final)
        }.onFailure { Log.warn("Failed to dump shader ${name}: ${it.message}") }
      }
      return final
    }

    /**
     * Arc's `Shader.preprocess`, applied explicitly.
     *
     * Arc runs this from `Shader`'s constructor, which sees a half-built object; doing it here
     * means the source kept (and dumped) is exactly the text the driver receives.
     *
     * An explicit `#version` is moved to the first line (GLSL requires it to precede everything
     * but comments), which is also why a preprocessor may safely prepend a `#define` header.
     */
    fun preprocessGLSL(source: String, fragment: Boolean, shaderFile: String? = null): String {
      val lines = source.split('\n')
      val versionIndex = lines.indexOfFirst { it.trimStart().startsWith("#version ") }

      // Arc rewrites `attribute` to `???` here, which only shows up as a confusing driver error.
      if (fragment && Regex("\\battribute\\b").containsMatchIn(stripComments(source))) {
        throw ArcRuntimeException(
          "`attribute` is not valid in a fragment shader${shaderFile?.let { " ($it)" } ?: ""}: use `varying`/`in` instead"
        )
      }

      val prelude =
        if (fragment) {
          val prec =
            if (source.contains("#define HIGHP") && !source.contains("//#define HIGHP")) "highp"
            else "mediump"
          """
          #ifdef GL_ES
          precision $prec float;
          precision mediump int;
          #else
          #define lowp  
          #define mediump 
          #define highp 
          #endif
          """.trimIndent()
        } else {
          """
          #ifndef GL_ES
          #define lowp  
          #define mediump 
          #define highp 
          #endif
          """.trimIndent()
        }

      // Blank the version line instead of dropping it so line numbers do not drift.
      val body =
        if (versionIndex >= 0) {
          lines.toMutableList().also { it[versionIndex] = "" }.joinToString("\n")
        } else source

      val versionLine = if (versionIndex >= 0) lines[versionIndex].trim() else null

      // Normally the Preprocessor's macro header already carries this; only add it when it does not,
      // so the define is never duplicated. It sits before `#line 1`, so it costs no line numbers.
      val fileDefine =
        if (shaderFile != null && !source.contains("#define ${POShader.SHADERFILE_MACRO} ")) {
          "#define ${POShader.SHADERFILE_MACRO} $shaderFile\n"
        } else ""

      // The preprocessed body carries its own `#line` directives, so this only seeds the mapping for
      // pipelines without a [Preprocessor]; a later directive overrides it either way.
      if (Core.gl30 == null) {
        return buildString {
          if (versionLine != null) append(versionLine).append('\n')
          append(prelude).append('\n')
          append(fileDefine)
          append("#line 1\n")
          append(body)
        }
      }

      val version =
        when {
          versionLine != null -> ""
          Core.app.isDesktop -> if (Core.graphics.getGLVersion().atLeast(3, 2)) "150" else "130"
          else -> "300 es"
        }

      // Rewrites only outside comments, so prose like "// varying" is left alone.
      val replaced = rewriteGlesLegacy(body, fragment)

      val outQualifier = if (Core.app.isMobile) " lowp" else ""
      val fragOut = if (needsFragColorOut(body, fragment)) "out$outQualifier vec4 fragColor;\n" else ""

      return buildString {
        append(if (versionLine == null) "#version $version\n" else "$versionLine\n")
        append(fragOut)
        append(prelude)
        append(fileDefine)
        append("#line 1\n")
        append(replaced)
      }
    }

    /**
     * Whether a fragment output declaration must be injected: only for fragments, and only when the
     * body does not already declare `fragColor`. Comments are ignored so a commented-out declaration
     * does not suppress the injection.
     */
    internal fun needsFragColorOut(body: String, fragment: Boolean): Boolean =
      fragment && !Regex("\\bout\\b[^;]*\\bfragColor\\b").containsMatchIn(stripComments(body))

    /**
     * Rewrites GLES2-era wording to the GL 3.x / GLES3 core vocabulary: `attribute` -> `in`,
     * `varying` -> `in`/`out`, `texture2D`/`textureCube` -> `texture`, `gl_FragColor` -> `fragColor`.
     *
     * Comments are skipped so prose is never rewritten.
     */
    internal fun rewriteGlesLegacy(body: String, fragment: Boolean): String =
      rewriteOutsideComments(body) { text ->
        text
          .replace(Regex("\\bvarying\\b"), if (fragment) "in" else "out")
          .replace(Regex("\\battribute\\b"), "in")
          .replace("texture2D(", "texture(")
          .replace("textureCube(", "texture(")
          .replace(Regex("\\bgl_FragColor\\b"), "fragColor")
      }

    /** Blanks out `//` and `/* */` comments so keyword checks cannot trip on prose. */
    private fun stripComments(source: String): String =
      source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)) { " ".repeat(it.value.length) }
        .replace(Regex("//[^\n]*")) { " ".repeat(it.value.length) }

    /**
     * Applies [rewrite] to the code parts of [source] only, leaving comments untouched.
     *
     * Splitting at comment boundaries is simpler and safer than trying to re-align offsets after a
     * length-changing rewrite.
     */
    private fun rewriteOutsideComments(source: String, rewrite: (String) -> String): String {
      val commentRegex = Regex("/\\*.*?\\*/|//[^\n]*", RegexOption.DOT_MATCHES_ALL)
      val sb = StringBuilder(source.length)
      var cursor = 0
      for (match in commentRegex.findAll(source)) {
        if (match.range.first > cursor) {
          sb.append(rewrite(source.substring(cursor, match.range.first)))
        }
        sb.append(match.value)
        cursor = match.range.last + 1
      }
      if (cursor < source.length) sb.append(rewrite(source.substring(cursor)))
      return sb.toString()
    }
  }
}

open class POShader(val source: POShaderSource) : Shader(source.vert, source.frag) {

  companion object {
    const val SHADERFILE_MACRO = "SHADER_FILE"

    fun infoProcess(source: String, file: String) = "#define $SHADERFILE_MACRO $file\n$source"

    /** Reads the file name back out of a source that was prepended with [infoProcess]. */
    fun getShaderFile(source: String): String =
      Regex("""(?m)^\s*#define\s+$SHADERFILE_MACRO\s+(\S+)\s*$""")
        .find(source)?.groupValues?.get(1)
        ?: throw ArcRuntimeException("Shader do not have macro $SHADERFILE_MACRO?!")

    /** Builds a shader from `shaders/<vert>.vert` + `shaders/<frag>.frag`. */
    fun of(vert: String, frag: String): POShader = POShader(vert, frag)

    fun of(vert: String, frag: String, builder: ProcessorsBuilder.() -> Unit): POShader =
      POShader(POShaderSource.of(vert, frag, builder))
  }

  constructor(
    vert: String,
    frag: String,
  ) : this(vert, frag, *POShaders.defaultProcessors.toTypedArray())

  constructor(
    vert: String,
    frag: String,
    vararg processors: ShaderProcessor,
  ) : this(POShaderSource.of(vert, frag, processors))

  constructor(
    vert: String,
    frag: String,
    builder: ProcessorsBuilder.() -> Unit,
  ) : this(POShaderSource.of(vert, frag, builder))

  val vertFi: Fi? get() = source.vertFi
  val fragFi: Fi? get() = source.fragFi
  val vertFile: String? get() = vertFi?.name()
  val fragFile: String? get() = fragFi?.name()

  /** Exactly the text handed to the driver. */
  val vertSource: String get() = source.vert
  val fragSource: String get() = source.frag

  private val uniformCache = HashMap<String, ShaderUniform>()

  /** Cached uniform handle; check [ShaderUniform.exists] before relying on it. */
  fun uniform(name: String): ShaderUniform =
    uniformCache.getOrPut(name) { ShaderUniform(this, name) }

  fun uniformOrNull(name: String): ShaderUniform? = uniform(name).takeIf { it.exists }

  /** Names of every uniform the driver reported as active. */
  fun uniformNames(): List<String> = getUniforms().toList()

  /** Writes the processed sources next to the game's data directory. */
  fun dump() {
    runCatching {
      val dir = Vars.dataDirectory.child("peroxicore/shaders/")
      vertFile?.let { dir.child(it).writeString(vertSource) }
      fragFile?.let { dir.child(it).writeString(fragSource) }
    }.onFailure { Log.warn("Failed to dump shader $vertFile/$fragFile: ${it.message}") }
  }

  /**
   * Already done by [POShaderSource]; Arc calls this from its constructor before any field of this
   * object exists, so it must stay a no-op.
   */
  protected override fun preprocess(source: String, fragment: Boolean): String = source
}

/** Location-cached uniform handle. Every setter is a no-op when the uniform was optimized out. */
class ShaderUniform internal constructor(
  private val shader: POShader,
  val name: String,
) {
  val location: Int = shader.fetchUniformLocation(name, false)
  val exists: Boolean get() = location >= 0

  fun f(v: Float) = apply { if (exists) shader.setUniformf(location, v) }

  fun f(x: Float, y: Float) = apply { if (exists) shader.setUniformf(location, x, y) }

  fun f(x: Float, y: Float, z: Float) = apply {
    if (exists) shader.setUniformf(location, x, y, z)
  }

  fun f(x: Float, y: Float, z: Float, w: Float) = apply {
    if (exists) shader.setUniformf(location, x, y, z, w)
  }

  fun vec2(v: Vec2) = apply { if (exists) shader.setUniformf(location, v) }

  fun vec3(v: Vec3) = apply { if (exists) shader.setUniformf(location, v) }

  fun color(c: Color) = apply { if (exists) shader.setUniformf(location, c) }

  fun i(v: Int) = apply { if (exists) shader.setUniformi(location, v) }

  fun ivec2(x: Int, y: Int) = apply { if (exists) shader.setUniformi(location, x, y) }

  fun ivec3(x: Int, y: Int, z: Int) = apply { if (exists) shader.setUniformi(location, x, y, z) }

  fun ivec4(x: Int, y: Int, z: Int, w: Int) = apply {
    if (exists) shader.setUniformi(location, x, y, z, w)
  }

  fun mat3(m: Mat) = apply { if (exists) shader.setUniformMatrix(location, m) }

  fun mat4(m: Mat) = apply {
    if (exists) shader.setUniformMatrix4fv(location, Shader.copyTransform(m), 0, 16)
  }

  fun mat4(values: FloatArray) = apply {
    if (exists) shader.setUniformMatrix4fv(location, values, 0, values.size)
  }
}
