package peroxicore.graphics

import arc.Core
import arc.graphics.gl.Shader
import arc.util.ArcRuntimeException
import mindustry.Vars

object POShaders {
  fun findFi(file: String) = Vars.tree.get("shaders/$file")!!

  val defaultProcessors = mutableListOf<ShaderProcessor>(IncludeProcessor())
}

open class POShader(vert: String, frag: String, vararg processors: ShaderProcessor) :
  Shader(
    processors.process(
      infoProcess(POShaders.findFi("$vert.vert").readString(), "$vert.vert"),
      false,
    ),
    processors.process(
      infoProcess(POShaders.findFi("$frag.frag").readString(), "$frag.frag"),
      true,
    ),
  ) {

  companion object {
    const val SHADERFILE_MACRO = "SHADER_FILE"
    val shaderFileRegex = """#define\s+$SHADERFILE_MACRO\s+(.+)""".toRegex()

    fun infoProcess(source: String, file: String) = "#define $SHADERFILE_MACRO $file\n$source"

    fun getShaderFile(source: String): String =
      shaderFileRegex.findAll(source).lastOrNull()?.groupValues?.get(1)?.trim()
        ?: throw ArcRuntimeException("Shader do not have macro $SHADERFILE_MACRO?!")
  }

  lateinit var vertFile: String
  lateinit var fragFile: String

  constructor(
    vert: String,
    frag: String,
  ) : this(vert, frag, *POShaders.defaultProcessors.toTypedArray())

  constructor(
    vert: String,
    frag: String,
    builder: ProcessorsBuilder.() -> Unit,
  ) : this(vert, frag, *shaderProcessors(builder).toTypedArray())

  protected override fun preprocess(source: String, fragment: Boolean): String {
    /*
    if (source.contains("#ifdef GL_ES"))
        throwShaderError("Shader contains GL_ES specific code", source)
    if (source.contains("#version"))
        throwShaderError("Shader contains explicit version requirement", source)
        */
    if (fragment) fragFile = getShaderFile(source) else vertFile = getShaderFile(source)

    var src =
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
        """
          .trimIndent() + "\n" + source
      } else {
        """
        #ifndef GL_ES
        #define lowp  
        #define mediump 
        #define highp 
        #endif
        """
          .trimIndent() + "\n" + source
      }

    Core.gl30?.let {
      val version =
        when {
          source.contains("#version ") -> ""
          Core.app.isDesktop -> if (Core.graphics.glVersion.atLeast(3, 2)) "150" else "130"
          else -> "300 es"
        }

      val outQualifier = if (Core.app.isMobile) " lowp" else ""
      val fragOut = if (fragment) "out${outQualifier} vec4 fragColor;\n" else ""

      val varyingRepl = if (fragment) "in" else "out"
      val attribRepl = if (fragment) "???" else "in"

      src = buildString {
        append("#version $version\n")
        append(fragOut)
        append(
          src
            .replace("varying", varyingRepl)
            .replace("attribute", attribRepl)
            .replace("texture2D(", "texture(")
            .replace("textureCube(", "texture(")
            .replace("gl_FragColor", "fragColor")
        )
      }
    }

    return src
  }

  protected open fun throwShaderError(reason: String, source: String): Nothing =
    throw ArcRuntimeException(
      "$reason; this should be handled by the preprocessor. Code: \n```\n$source\n```"
    )
}
