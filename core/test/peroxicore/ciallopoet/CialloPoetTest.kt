package peroxicore.ciallopoet

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*
import kotlin.test.*

class CialloPoetTest {
  /** Generated text must be accepted by Ciallo's own formatter unchanged. */
  private fun assertCanonical(source: String) {
    assertEquals(source, CialloFormatter.format(source))
  }

  private fun run(source: String): List<String> {
    val lines = mutableListOf<String>()
    CialloEngine(CialloEnvironment(output = { lines += it })).execute(source)
    return lines
  }

  @Test
  fun stringLiteralsEscapeQuotesBackslashesAndBraces() {
    assertEquals("\"say \\\"hi\\\"\"", CialloLiterals.stringLiteral("say \"hi\""))
    assertEquals("\"a\\\\b\"", CialloLiterals.stringLiteral("a\\b"))
    // Braces are escaped so a literal never becomes an interpolation.
    assertEquals("\"x = \\{1\\}\"", CialloLiterals.stringLiteral("x = {1}"))
    assertEquals("\"a\\nb\"", CialloLiterals.stringLiteral("a\nb"))
  }

  @Test
  fun generatedStringRoundTripsThroughTheInterpreter() {
    val value = "a {b} \"c\" d"
    val lines = run("print " + CialloLiterals.stringLiteral(value))
    assertEquals(listOf(value), lines)
  }

  @Test
  fun numberLiteralsKeepFloatsFloatAndRejectOutOfRangeLongs() {
    assertEquals("7", CialloLiterals.numberLiteral(7))
    assertEquals("2.5", CialloLiterals.numberLiteral(2.5))
    assertEquals("1.5", CialloLiterals.numberLiteral(1.5f))
    assertEquals("3.0", CialloLiterals.numberLiteral(3.0))
    assertFailsWith<IllegalArgumentException> {
      CialloLiterals.numberLiteral(Int.MAX_VALUE.toLong() + 1)
    }
  }

  @Test
  fun valueLiteralsCoverTheSpellableKinds() {
    assertEquals("null", CialloLiterals.valueLiteral(CialloNil.INSTANCE))
    assertEquals("true", CialloLiterals.valueLiteral(CialloBool(true)))
    assertEquals("5", CialloLiterals.valueLiteral(CialloInt(5)))
    assertEquals("\"x\"", CialloLiterals.valueLiteral(CialloStr("x")))
    assertEquals("[1, 2]", CialloLiterals.valueLiteral(CialloArr(mutableListOf(CialloInt(1), CialloInt(2)))))
    assertEquals("5s", CialloLiterals.valueLiteral(CialloTime(5.0, CialloTimeUnit.Second)))
    assertEquals("@x", CialloLiterals.valueLiteral(CialloSymbol(listOf("x"))))
    // Objects have no literal syntax and must be rejected instead of emitted as invalid source.
    assertFailsWith<IllegalArgumentException> {
      CialloLiterals.valueLiteral(CialloObj(linkedMapOf("a" to CialloInt(1))))
    }
  }

  @Test
  fun nameAndSymbolLiteralsValidatePaths() {
    assertEquals("Blocks.duo", CialloLiterals.nameLiteral("Blocks.duo"))
    assertEquals("@Blocks.duo", CialloLiterals.symbolLiteral("Blocks.duo"))
    assertFailsWith<IllegalArgumentException> { CialloLiterals.nameLiteral("1abc") }
    assertFailsWith<IllegalArgumentException> { CialloLiterals.nameLiteral("true") }
  }

  @Test
  fun codeBlockPlaceholders() {
    assertEquals("label \"hi\"", CialloCodeBlock.of("label %S", "hi").toString())
    assertEquals("print @x", CialloCodeBlock.of("print %A", "x").toString())
    assertEquals("Blocks.duo", CialloCodeBlock.of("%N", "Blocks.duo").toString())
    assertEquals("100%", CialloCodeBlock.of("100%%").toString())
    assertEquals("f(1, 2)", CialloCodeBlock.of("f(%L, %L)", 1, 2).toString())
  }

  @Test
  fun codeBlockRejectsUnusedAndMissingArguments() {
    assertFailsWith<IllegalArgumentException> { CialloCodeBlock.of("%L", 1, 2) }
    assertFailsWith<IllegalArgumentException> { CialloCodeBlock.of("%L %L", 1) }
    assertFailsWith<IllegalArgumentException> { CialloCodeBlock.of("%L %1L", 1) }
  }

  @Test
  fun codeBlockIndentControlsFollowingLines() {
    val block = buildCodeBlock {
      addStatement("run {")
      withIndent { addStatement("print %S", "a") }
      addStatement("}")
    }
    assertEquals("run {\n  print \"a\"\n}\n", block.toString())
    assertCanonical(block.toString())
  }

  @Test
  fun blockSpecEmitsBracesAndStatements() {
    val block =
      CialloBlockSpec.builder()
        .addStatement("%A local %L", "x", 1)
        .addStatement("print @x")
        .build()
    assertEquals("{\n  @x local 1\n  print @x\n}", block.toString())
    assertCanonical("run " + block + "\n")
  }

  @Test
  fun emptyBlockSpecIsCompact() {
    assertEquals("{}", CialloBlockSpec.builder().build().toString())
    assertEquals("then {}", CialloChainSpec.call("then", CialloBlockSpec.builder().build()).toString())
  }

  @Test
  fun chainSpecSpellsTheDocumentedExample() {
    val chain =
      CialloChainSpec.builder("build")
        .addValueArgument("%A", "Blocks.duo")
        .addCall("at", 4, 3)
        .addMember("config")
        .addValue(null)
        .build()
    assertEquals("build @Blocks.duo at(4, 3) config null", chain.toString())
    assertCanonical(chain.toString() + "\n")
  }

  @Test
  fun chainValueArgumentAndParenthesisedCallDiffer() {
    assertEquals("label \"hi\"", CialloChainSpec.builder("label").addValueArgument("%S", "hi").build().toString())
    // %L is raw by design: a String argument is emitted unquoted, so use %S for string literals.
    assertEquals("label(hi)", CialloChainSpec.call("label", "hi").toString())
    assertEquals("label(\"hi\")", CialloChainSpec.builder("label").addArgument("%S", "hi").build().toString())
  }

  @Test
  fun chainAttachesBlockToCall() {
    val body = CialloBlockSpec.builder().addStatement("print @it").build()
    val chain = CialloChainSpec.builder("run").addArgument("%L", 1).block(body).build()
    assertEquals("run(1) {\n  print @it\n}", chain.toString())
    assertCanonical(chain.toString() + "\n")
  }

  @Test
  fun chainRejectsValueAfterCompletedCall() {
    assertFailsWith<IllegalStateException> {
      CialloChainSpec.builder("f").addCall("g", 1).addValue(2).build()
    }
  }

  @Test
  fun chainRejectsArgumentsOnALiteralHead() {
    assertFailsWith<IllegalStateException> {
      CialloChainSpec.literalBuilder(1).addArgument("%L", 2).build()
    }
    assertFailsWith<IllegalStateException> {
      CialloChainSpec.symbolBuilder("x").addArgument("%L", 2).build()
    }
  }

  @Test
  fun fileSpecEmitsCommentThenStatements() {
    val file =
      CialloFileSpec.builder("Demo")
        .addFileComment("generated")
        .addStatement("print %S", "start")
        .addCode("%L", CialloChainSpec.builder("world").block(CialloBlockSpec.builder().addStatement("print \"x\"").build()).build())
        .build()
    val text = file.toString()
    assertTrue(text.startsWith("// generated\n"), text)
    assertTrue("print \"start\"\n" in text, text)
    assertTrue("world {\n  print \"x\"\n}\n" in text, text)
  }

  @Test
  fun fileSpecOutputIsCanonicalAndRunnable() {
    val file =
      CialloFileSpec.builder("Demo")
        .addStatement("print %S", "start")
        .addCode("%L", CialloChainSpec.builder("run").block(
          CialloBlockSpec.builder()
            .addStatement("%A local %L", "x", 2)
            .addStatement("print @x")
            .build(),
        ).build())
        .build()
    val text = file.toString()
    assertCanonical(text)
    assertEquals(listOf("start", "2"), run(text))
  }

  @Test
  fun nameAllocatorAvoidsReservedWordsAndCollisions() {
    val allocator = CialloNameAllocator()
    assertEquals("x", allocator.newName("x", tag = "first"))
    assertEquals("x2", allocator.newName("x", tag = "second"))
    // A tag keeps its name across calls.
    assertEquals("x", allocator.newName("x", tag = "first"))
    // Reserved words and invalid identifiers are made safe.
    assertEquals("local2", allocator.newName("local", tag = "reserved"))
    assertEquals("_1abc", allocator.newName("1abc", tag = "digit"))
    assertEquals("a_b", allocator.newName("a b", tag = "space"))
  }
}
