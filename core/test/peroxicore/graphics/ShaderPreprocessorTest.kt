package peroxicore.graphics

import arc.files.Fi
import arc.util.ArcRuntimeException
import java.io.File
import kotlin.test.*
import mindustry.Vars

class ShaderPreprocessorTest {
  private lateinit var root: Fi

  @BeforeTest
  fun setUp() {
    root = Fi.tempDirectory("peroxicore-shader-test")
    POShaders.macros.clear()
    POShaders.includePaths.clear()
    POShaders.clearCache()
    Vars.tree.clear()
  }

  @AfterTest
  fun tearDown() {
    Vars.tree.clear()
    POShaders.clearCache()
    POShaders.macros.clear()
    POShaders.includePaths.clear()
    root.deleteDirectory()
  }

  /** Writes [text] into the temp dir and registers it as `shaders/[path]`, like a mod would. */
  private fun shaderFile(path: String, text: String): Fi {
    val fi = root.child(path)
    fi.writeString(text)
    Vars.tree.addFile("shaders/$path", fi)
    return fi
  }

  private fun process(
    source: String,
    file: Fi? = null,
    preprocessor: Preprocessor = Preprocessor(),
    defines: Map<String, String?> = emptyMap(),
  ): String = preprocessor.process(source, ShaderContext(false, file, LinkedHashMap(defines)))

  // ------------------------------------------------------------------ includes

  @Test
  fun includeResolvesRelativeToIncludingFile() {
    shaderFile("lib/helper.glsl", "float helper();\n")
    val main = shaderFile("main.glsl", "#include \"lib/helper.glsl\"\nvoid main() {}\n")

    val out = process("#include \"lib/helper.glsl\"\nvoid main() {}\n", main)

    assertTrue(out.contains("float helper();"), out)
    assertFalse(out.contains("#include"), "include 指令不应残留: $out")
    assertTrue(out.contains("void main()"), "include 之后的源文件内容必须保留")
  }

  @Test
  fun includeWithLeadingSlashResolvesFromShaderRoot() {
    shaderFile("shared/colors.glsl", "vec3 red = vec3(1);\n")
    val main = shaderFile("deep/nested/main.glsl", "\n")

    val out = process("#include \"/shared/colors.glsl\"\n", main)

    assertTrue(out.contains("vec3 red"), out)
  }

  @Test
  fun includeFallsBackToSearchPaths() {
    shaderFile("common/noise.glsl", "float noise() { return 0.0; }\n")
    val main = shaderFile("main.glsl", "\n")

    val out =
      process(
        "#include \"noise.glsl\"\n",
        main,
        preprocessor = Preprocessor(includePaths = listOf("common")),
      )

    assertTrue(out.contains("float noise()"), out)
  }

  @Test
  fun includeSupportsAngleBrackets() {
    shaderFile("util.glsl", "float util();\n")

    val out = process("#include <util.glsl>\n", shaderFile("main.glsl", "\n"))

    assertTrue(out.contains("float util();"), out)
  }

  @Test
  fun globalIncludePathsAreUsed() {
    shaderFile("common/x.glsl", "float x();\n")
    POShaders.includePaths += "common"

    val out = process("#include \"x.glsl\"\n", shaderFile("main.glsl", "\n"))

    assertTrue(out.contains("float x();"), out)
  }

  @Test
  fun pragmaOnceIncludesOnlyOnce() {
    shaderFile("shared.glsl", "#pragma once\nfloat shared();\n")
    val main = shaderFile("main.glsl", "\n")

    val out = process("#include \"shared.glsl\"\n#include \"shared.glsl\"\n", main)

    assertEquals(1, Regex("float shared").findAll(out).count(), "#pragma once 应只展开一次: $out")
    assertFalse(out.contains("#pragma"), "#pragma 不应传给驱动: $out")
  }

  @Test
  fun diamondIncludesAreNotTreatedAsCycles() {
    shaderFile("leaf.glsl", "float leaf();\n")
    shaderFile("a.glsl", "#include \"leaf.glsl\"\n")
    shaderFile("b.glsl", "#include \"leaf.glsl\"\n")
    val main = shaderFile("main.glsl", "\n")

    val out = process("#include \"a.glsl\"\n#include \"b.glsl\"\n", main)

    // 没有 #pragma once 时允许重复展开，但绝不能误报循环。
    assertEquals(2, Regex("float leaf").findAll(out).count(), out)
  }

  @Test
  fun circularIncludeIsReportedWithChain() {
    shaderFile("a.glsl", "#include \"b.glsl\"\n")
    shaderFile("b.glsl", "#include \"a.glsl\"\n")
    val main = shaderFile("main.glsl", "\n")

    val error = assertFailsWith<ArcRuntimeException> { process("#include \"a.glsl\"\n", main) }

    val message = error.message.orEmpty()
    assertTrue(message.contains("Circular include"), message)
    assertTrue(message.contains("a.glsl"), "错误信息应给出循环链: $message")
    assertTrue(message.contains("b.glsl"), "错误信息应给出循环链: $message")
  }

  @Test
  fun missingIncludeIsReportedWithLocation() {
    val main = shaderFile("main.glsl", "\n")

    val error = assertFailsWith<ArcRuntimeException> { process("#include \"nope.glsl\"\n", main) }

    val message = error.message.orEmpty()
    assertTrue(message.contains("Include not found"), message)
    assertTrue(message.contains("nope.glsl"), message)
    assertTrue(message.contains("main.glsl:1"), "应指出出错文件与行号: $message")
  }

  @Test
  fun malformedIncludeIsReported() {
    shaderFile("main.glsl", "\n")

    val error = assertFailsWith<ArcRuntimeException> {
      process("#include not-a-string\n", shaderFile("main.glsl", "\n"))
    }

    val message = error.message.orEmpty()
    assertTrue(message.contains("Malformed #include"), message)
  }

  @Test
  fun inactiveBranchDoesNotResolveIncludes() {
    val main = shaderFile("main.glsl", "\n")

    val out =
      process(
        """
        #if 0
          #include "does-not-exist.glsl"
        #endif
        void main() {}
        """.trimIndent() + "\n",
        main,
      )

    assertFalse(out.contains("does-not-exist"), "非活跃分支的 include 不应被解析: $out")
    assertTrue(out.contains("void main()"), out)
  }

  @Test
  fun includeDepthLimitIsEnforced() {
    shaderFile("d1.glsl", "#include \"d2.glsl\"\n")
    shaderFile("d2.glsl", "#include \"d3.glsl\"\n")
    shaderFile("d3.glsl", "float deep();\n")
    val main = shaderFile("main.glsl", "\n")

    val error = assertFailsWith<ArcRuntimeException> {
      process("#include \"d1.glsl\"\n", main, preprocessor = Preprocessor(maxIncludeDepth = 1))
    }

    val message = error.message.orEmpty()
    assertTrue(message.contains("Include depth exceeded"), message)
  }

  @Test
  fun includesInsideIncludedFilesResolveRelativeToThatFile() {
    shaderFile("lib/a.glsl", "#include \"b.glsl\"\n")
    shaderFile("lib/b.glsl", "float b();\n")
    val main = shaderFile("main.glsl", "\n")

    val out = process("#include \"lib/a.glsl\"\n", main)

    assertTrue(out.contains("float b();"), out)
  }

  // -------------------------------------------------------------- conditionals

  @Test
  fun ifdefChoosesBranchAndBlanksTheOther() {
    val source =
      """
      #ifdef ON
        float on();
      #else
        float off();
      #endif
      """.trimIndent() + "\n"

    val out = process(source, defines = mapOf("ON" to "1"))

    assertTrue(out.contains("float on();"), out)
    assertFalse(out.contains("float off();"), "未选中的分支不能出现在输出里: $out")
    assertFalse(out.contains("#ifdef"), "条件指令应由预处理器处理: $out")
  }

  @Test
  fun ifndefIsTheInverseOfIfdef() {
    val source = """
      #ifndef MISSING
        float visible();
      #endif
    """.trimIndent() + "\n"

    assertTrue(process(source).contains("float visible()"), "未定义时应进入 #ifndef 分支")
    assertFalse(
      process(source, defines = mapOf("MISSING" to null)).contains("float visible()"),
      "已定义时不应进入 #ifndef 分支",
    )
  }

  @Test
  fun elifChainTakesFirstTrueBranch() {
    val source = """
      #if LEVEL == 1
        float one();
      #elif LEVEL == 2
        float two();
      #elif LEVEL == 3
        float three();
      #else
        float none();
      #endif
    """.trimIndent() + "\n"

    val out = process(source, defines = mapOf("LEVEL" to "2"))
    assertTrue(out.contains("float two();"), out)
    assertFalse(out.contains("float one();"), out)
    assertFalse(out.contains("float three();"), out)
    assertFalse(out.contains("float none();"), out)
  }

  @Test
  fun nestedConditionalsRequireEveryLevelActive() {
    val source = """
      #ifdef OUTER
        #ifdef INNER
          float in();
        #else
          float outOnly();
        #endif
      #endif
    """.trimIndent() + "\n"

    assertTrue(process(source, defines = mapOf("OUTER" to null, "INNER" to null)).contains("float in();"))
    val partial = process(source, defines = mapOf("OUTER" to null))
    assertTrue(partial.contains("float outOnly();"), partial)
    assertFalse(partial.contains("float in();"), partial)
    assertFalse(process(source).contains("float"), "外层不成立时整块都不应出现")
  }

  @Test
  fun definedOperatorAcceptsBothForms() {
    val paren = "#if defined(FOO)\nfloat yes();\n#endif\n"
    val bare = "#if defined FOO\nfloat yes();\n#endif\n"
    val negated = "#if !defined(FOO)\nfloat no();\n#endif\n"

    assertTrue(process(paren, defines = mapOf("FOO" to null)).contains("float yes()"))
    assertTrue(process(bare, defines = mapOf("FOO" to null)).contains("float yes()"))
    assertFalse(process(paren).contains("float yes()"))
    assertTrue(process(negated).contains("float no()"))
  }

  @Test
  fun undefinedIdentifiersEvaluateToZero() {
    assertFalse(process("#if UNDEFINED\nfloat on();\n#endif\n").contains("float on()"))
    assertTrue(process("#if !UNDEFINED\nfloat on();\n#endif\n").contains("float on()"))
  }

  @Test
  fun emptyMacroBodyCountsAsTrue() {
    // 这是相对 C 的刻意放宽：#define DEBUG 之后 #if DEBUG 应当成立。
    assertTrue(process("#define DEBUG\n#if DEBUG\nfloat on();\n#endif\n").contains("float on()"))
  }

  @Test
  fun macroBodyIsEvaluatedAsExpression() {
    val source = "#define SHIFT (1 << 3)\n#if SHIFT == 8\nfloat on();\n#endif\n"

    assertTrue(process(source).contains("float on()"), "宏体应作为表达式求值")
  }

  @Test
  fun arithmeticAndPrecedenceFollowC() {
    val cases =
      listOf(
        "1" to true,
        "0" to false,
        "1 + 2 * 3 == 7" to true,
        "(1 + 2) * 3 == 9" to true,
        "1 << 3 == 8" to true,
        "16 >> 2 == 4" to true,
        "7 & 3 == 3" to true,
        "3 | 4 == 7" to true,
        "7 ^ 3 == 4" to true,
        "1 && 0" to false,
        "1 || 0" to true,
        "!0 && !0" to true,
        "~0 == -1" to true,
        "10 % 4 == 2" to true,
        "2 >= 2 && 3 <= 3" to true,
        "1 != 1" to false,
      )

    for ((expression, expected) in cases) {
      val out = process("#if $expression\nfloat on();\n#endif\n")
      assertEquals(expected, out.contains("float on()"), "#if $expression 的判定结果不对")
    }
  }

  @Test
  fun defineAndUndefAreTracked() {
    val source = """
      #define FLAG 1
      #undef FLAG
      #ifdef FLAG
        float still();
      #endif
      float tail();
    """.trimIndent() + "\n"

    val out = process(source)
    assertFalse(out.contains("float still()"), "#undef 之后的宏不应再生效")
    assertTrue(out.contains("float tail()"), out)
  }

  @Test
  fun undefInActiveBranchOnly() {
    val source = """
      #define FLAG 1
      #if 0
        #undef FLAG
      #endif
      #ifdef FLAG
        float kept();
      #endif
    """.trimIndent() + "\n"

    assertTrue(process(source).contains("float kept()"), "非活跃分支里的 #undef 必须被忽略")
  }

  @Test
  fun functionLikeMacroCountsAsDefined() {
    val source = "#define MAX(a, b) ((a) > (b) ? (a) : (b))\n#ifdef MAX\nfloat on();\n#endif\n"

    assertTrue(process(source).contains("float on()"), "函数式宏也应算作已定义")
  }

  @Test
  fun contextMacrosAreVisibleToConditionals() {
    val out = process("#ifdef INJECTED\nfloat on();\n#endif\n", defines = mapOf("INJECTED" to "1"))

    assertTrue(out.contains("float on()"), out)
  }

  @Test
  fun globalMacrosAreVisibleToConditionals() {
    POShaders.macros["GLOBAL_FLAG"] = "1"
    val out = process("#if GLOBAL_FLAG\nfloat on();\n#endif\n")

    assertTrue(out.contains("float on()"), out)
  }

  @Test
  fun globalMacrosAreEmittedAsHeader() {
    POShaders.macros["SHARED"] = "42"
    val out = process("float body();\n")

    assertTrue(out.contains("#define SHARED 42"), "全局宏应写入头部: $out")
  }

  @Test
  fun conditionalsCanBeDisabledToPassDirectivesThrough() {
    val source = "#ifdef FOO\nfloat a();\n#endif\n"

    val out = process(source, preprocessor = Preprocessor(conditionals = false))

    assertTrue(out.contains("#ifdef FOO"), "关闭条件处理后指令应原样保留: $out")
    assertTrue(out.contains("#endif"), out)
    assertTrue(out.contains("float a();"), "关闭条件处理后所有分支都应保留: $out")
  }

  @Test
  fun strayConditionalDirectivesAreReported() {
    assertFailsWith<ArcRuntimeException> { process("#endif\n") }
    assertFailsWith<ArcRuntimeException> { process("#else\n") }
    assertFailsWith<ArcRuntimeException> { process("#elif 1\n") }
  }

  @Test
  fun unterminatedConditionalIsReported() {
    val error = assertFailsWith<ArcRuntimeException> { process("#if 1\nfloat a();\n") }

    val message = error.message.orEmpty()
    assertTrue(message.contains("unterminated"), message)
  }

  @Test
  fun unmatchedParenthesisInIfIsReported() {
    val error = assertFailsWith<ArcRuntimeException> { process("#if (1 +\n#endif\n") }

    val message = error.message.orEmpty()
    assertTrue(message.contains("Bad #if expression"), message)
  }

  // ------------------------------------------------------------- line mapping

  @Test
  fun removedLinesAreReplacedWithBlanks() {
    // 只有被预处理器消费掉的指令（条件、include 等）才置空；#define 会透传给驱动。
    // 无论哪种情况，正文的行号都不能偏移。
    val out = process("#if 1\n#define A 1\n#endif\nfloat body();\n")

    val bodyLines = out.substringAfter("#line 1\n").lines()
    assertEquals("", bodyLines[0], "被消费的 #if 应变成空行")
    assertEquals("#define A 1", bodyLines[1], "#define 应透传给驱动")
    assertEquals("", bodyLines[2], "被消费的 #endif 应变成空行")
    assertEquals("float body();", bodyLines[3], "正文行号不能偏移")
  }

  @Test
  fun lineDirectivesBracketIncludes() {
    shaderFile("inc.glsl", "float inc();\n")
    val main = shaderFile("main.glsl", "\n")

    val out = process("float before();\n#include \"inc.glsl\"\nfloat after();\n", main)

    // 映射前面可以有一行注释标出文件名，但正文必须以 `#line 1` 开始。
    val mapping = out.lines().dropWhile { !it.startsWith("#line") }
    assertEquals("#line 1", mapping.firstOrNull(), "正文前必须给出起始映射: $out")
    assertEquals(2, Regex("(?m)^#line 1$").findAll(out).count(), "#line 1 应同时出现在文件头与 include 内: $out")
    assertTrue(out.contains("#line 3"), "include 之后应恢复到原文件的行号: $out")
    assertTrue(out.contains("inc.glsl"), "映射应注明文件路径以便排查: $out")
  }

  @Test
  fun lineDirectivesCanBeDisabled() {
    shaderFile("inc.glsl", "float inc();\n")
    val main = shaderFile("main.glsl", "\n")

    val out = process("float before();\n#include \"inc.glsl\"\nfloat after();\n", main, preprocessor = Preprocessor(lineDirectives = false))

    assertFalse(out.contains("#line"), "关闭后不应出现 #line: $out")
    assertTrue(out.contains("float inc();"), out)
  }

  @Test
  fun errorsInIncludedFilePointAtThatFile() {
    shaderFile("broken.glsl", "\n#if (1 +\n#endif\n")
    val main = shaderFile("main.glsl", "\n")

    val error = assertFailsWith<ArcRuntimeException> { process("#include \"broken.glsl\"\n", main) }

    val message = error.message.orEmpty()
    assertTrue(message.contains("broken.glsl:2"), "错误应指向被 include 的文件行号: $message")
    assertTrue(message.contains("include chain"), "错误应给出 include 链: $message")
  }

  // --------------------------------------------------------- error/warn/#version

  @Test
  fun errorDirectiveThrowsWithMessage() {
    val error = assertFailsWith<ArcRuntimeException> { process("#error 不支持该平台\n") }

    val message = error.message.orEmpty()
    assertTrue(message.contains("不支持该平台"), message)
  }

  @Test
  fun errorDirectiveInInactiveBranchIsIgnored() {
    val out = process("#if 0\n#error should not fire\n#endif\nfloat ok();\n")

    assertTrue(out.contains("float ok()"), out)
  }

  @Test
  fun warningDirectiveDoesNotThrow() {
    val out = process("#warning 只是提醒\nfloat ok();\n")

    assertTrue(out.contains("float ok()"), out)
    assertFalse(out.contains("#warning"), "警告不应传给驱动: $out")
  }

  @Test
  fun versionAndExtensionDirectivesArePassedThrough() {
    val out = process("#version 300 es\n#extension GL_OES_standard_derivatives : enable\nfloat ok();\n")

    assertTrue(out.contains("#version 300 es"), out)
    assertTrue(out.contains("#extension GL_OES_standard_derivatives"), out)
  }

  @Test
  fun defineDirectiveIsPassedThroughForTheDriver() {
    val out = process("#define HIGHP\nfloat ok();\n")

    assertTrue(out.contains("#define HIGHP"), "源码中的 #define 需要留给驱动: $out")
  }

  // --------------------------------------------------------------- edge cases

  @Test
  fun handlesCarriageReturnLineEndings() {
    val out = process("#define A 1\r\n#if A\r\nfloat ok();\r\n#endif\r\n")

    assertTrue(out.contains("float ok();"), out)
    assertFalse(out.contains("\r"), "行尾 CR 应被去掉: $out")
  }

  @Test
  fun emptySourceProducesOnlyHeaderAndMapping() {
    val out = process("", preprocessor = Preprocessor(lineDirectives = false))

    assertEquals("", out.trim(), "空源码不应产生任何内容")
  }

  @Test
  fun includeAtEndOfFileDoesNotLoseContent() {
    shaderFile("tail.glsl", "float tail();\n")
    val main = shaderFile("main.glsl", "\n")

    val out = process("float head();\n#include \"tail.glsl\"", main)

    assertTrue(out.contains("float head();"), out)
    assertTrue(out.contains("float tail();"), "文件末尾的 include 也必须展开: $out")
  }

  @Test
  fun processorIsReusableAcrossStages() {
    // defaultProcessors 里共享同一个 Preprocessor 实例，两次 process 之间不能残留宏。
    val preprocessor = Preprocessor()

    val first = preprocessor.process("#define ONLY_HERE\n", ShaderContext(false, null))
    val second = preprocessor.process("#ifdef ONLY_HERE\nfloat leaked();\n#endif\n", ShaderContext(true, null))

    assertTrue(first.contains("#define ONLY_HERE"), first)
    assertFalse(second.contains("float leaked()"), "宏状态不能在两次调用之间泄漏: $second")
  }

  @Test
  fun fileDefineMacroIsAvailableToShaders() {
    val main = shaderFile("main.glsl", "\n")
    val context = ShaderContext(false, main, linkedMapOf(POShader.SHADERFILE_MACRO to "main.glsl"))

    val out = Preprocessor().process("#ifdef ${POShader.SHADERFILE_MACRO}\nfloat tagged();\n#endif\n", context)

    assertTrue(out.contains("float tagged()"), "#ifdef SHADER_FILE 应当可用: $out")
    assertTrue(out.contains("#define ${POShader.SHADERFILE_MACRO} main.glsl"), "文件名宏应写入头部: $out")
  }

  @Test
  fun buildPipelinePlacesMacrosBeforePreprocessor() {
    val built =
      shaderProcessors {
        preprocessor()
        macro("AFTER" to "1")
      }

    assertEquals(
      listOf("MacroProcessor", "Preprocessor"),
      built.map { it::class.simpleName },
      "宏阶段必须排在预处理器之前，否则宏无法影响条件编译",
    )
  }

  @Test
  fun builderSkipsDuplicateDefaultPreprocessor() {
    val built =
      shaderProcessors {
        preprocessor(conditionals = false)
      }

    assertEquals(
      1,
      built.count { it is Preprocessor },
      "显式添加的预处理器应替换默认实例，避免 include 被展开两次: $built",
    )
    assertFalse((built.single() as Preprocessor).conditionals, "应保留调用方指定的配置")
  }

  @Test
  fun includeProcessorRespectsCustomResolution() {
    // resolveInclude 是开放点，调用方可以换掉整个解析策略。
    val custom =
      object : Preprocessor() {
        override fun resolveInclude(name: String, from: Fi?): Fi? =
          if (name == "virtual") Fi(File(root.file(), "virtual.glsl")).also { it.writeString("float virtual();\n") }
          else super.resolveInclude(name, from)
      }

    val out = custom.process("#include \"virtual\"\n", ShaderContext(false, null))

    assertTrue(out.contains("float virtual();"), out)
  }
}
