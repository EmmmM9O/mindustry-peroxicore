package peroxicore.ciallo

import kotlin.test.*

class CialloReflectionTarget {
  @JvmField
  var value: Int = 7

  fun double(input: Int): Int = input * 2
}

class CialloStaticTarget {
  companion object {
    @JvmField
    var value: Int = 5
  }
}

class CialloLangTest {
  private fun runLines(source: String): List<String> {
    val lines = mutableListOf<String>()
    CialloEngine(CialloEnvironment(output = { lines += it })).execute(source)
    return lines
  }

  /*
  private fun runWithSymbols(source: String): Pair<List<String>, CialloScene> {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    environment.symbol("Blocks.duo", CialloValue.Str("duo"))
    environment.symbol("Blocks.green", CialloValue.Str("green"))
    CialloEngine(environment).execute(source)
    return lines to environment.scene
  }*/

  @Test
  fun thenBlockRuns() {
    assertEquals(listOf("then"), runLines("then{ print \"then\" }"))
  }

  @Test
  fun runBlockBindsIt() {
    assertEquals(listOf("1"), runLines("run(1){ print @it }"))
  }

  @Test
  fun repeatIfTrueAndLoop() {
    assertEquals(
      listOf("0", "1", "yes"),
      runLines("repeat(2){ print @it }\nifTrue(true){ print \"yes\" }"),
    )
    assertEquals(
      listOf("0", "1", "2"),
      runLines("@i local 0\nloop{\n  print @i\n  @i set(@i add(1))\n  @i less(3)\n}"),
    )
  }

  @Test
  fun localThenPrint() {
    assertEquals(listOf("1"), runLines("@x local 1\nprint @x"))
  }

  @Test
  fun mountInsideBlockIsVisibleOutside() {
    assertEquals(listOf("2"), runLines("then{\n  @y mount 2\n}\nprint @y"))
  }

  @Test
  fun localShadowsGlobal() {
    assertEquals(
      listOf("2", "1"),
      runLines("@x mount 1\nthen{\n  @x local 2\n  print @x\n}\nprint @x"),
    )
  }

  @Test
  fun setWalksOuterScope() {
    assertEquals(listOf("2"), runLines("@x local 1\nthen{ @x set 2 }\nprint @x"))
  }

  @Test
  fun setWalksToGlobalScope() {
    assertEquals(listOf("2"), runLines("@x mount 1\nthen{ @x set 2 }\nprint @x"))
  }

  @Test
  fun setWalksToOuterBlockLocal() {
    assertEquals(
      listOf("2"),
      runLines("then{\n  @x local 1\n  then{\n    @x set 2\n  }\n  print @x\n}"),
    )
  }

  @Test
  fun duplicateLocalFails() {
    assertFailsWith<CialloException> {
      CialloEngine().execute("@x local 1\n@x local 2")
    }
  }

  @Test
  fun duplicateMountFails() {
    assertFailsWith<CialloException> {
      CialloEngine().execute("@x mount 1\n@x mount 2")
    }
  }

  @Test
  fun setUndefinedFails() {
    assertFailsWith<CialloException> {
      CialloEngine().execute("@x set 1")
    }
  }

  @Test
  fun keywordCannotBeVariableName() {
    assertFailsWith<CialloException> {
      CialloEngine().execute("@local local 1")
    }
  }

  @Test
  fun multilineProgramAndComments() {
    val source =
      """
      // leading comment
      @a local 1
      /* block comment */
      @b local 2
      print @a
      print @b
      // trailing comment
      """
    assertEquals(listOf("1", "2"), runLines(source))
  }

  @Test
  fun stringInterpolation() {
    assertEquals(listOf("value is 7"), runLines("@x local 7\nprint \"value is {@x}\" "))
  }

  @Test
  fun longStringSupportsNewlines() {
    assertEquals(listOf("line1\nline2"), runLines("print \"\"\"line1\nline2\"\"\""))
  }

  @Test
  fun arraysAndMixedNumbers() {
    assertEquals(listOf("[1, 2.5, x, true, null]"), runLines("print [1, 2.5, \"x\", true, null]"))
  }

  @Test
  fun mixedArithmeticProducesFloat() {
    assertEquals(listOf("3", "3.5"), runLines("print(1 add(2))\nprint(1 add(2.5))"))
  }

  @Test
  fun timeLiterals() {
    assertEquals(listOf("5s", "1.5m", "2h"), runLines("print 5s\nprint 1.5m\nprint 2h"))
  }

  @Test
  fun dotFormOfVariableDefinition() {
    assertEquals(listOf("1"), runLines("@x.local(1)\nprint @x"))
  }

  @Test
  fun setAcceptsParenthesizedExpression() {
    assertEquals(listOf("3"), runLines("@x local 1\n@x set(2 add(1))\nprint @x"))
  }

  @Test
  fun bareNameLiteralStatementFails() {
    assertFailsWith<CialloRuntimeException> {
      CialloEngine().execute("@x")
    }
  }

  @Test
  fun bareIdentifierUnparenthesizedArgumentIsRejected() {
    assertFailsWith<CialloException> {
      CialloEngine().execute("print x")
    }
  }

  @Test
  fun sandboxBlocksJvmAccess() {
    val environment = CialloEnvironment(output = { }, sandbox = CialloSandbox.SAFE)
    environment.symbol("host", CialloValue.JavaValue(Any()))
    assertFailsWith<CialloSandboxException> {
      CialloEngine(environment).execute("@host toString()")
    }
  }

  @Test
  fun fullSandboxAllowsJvmFieldsAndMethods() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it }, sandbox = CialloSandbox.FULL)
    environment.symbol("host", CialloValue.JavaValue(CialloReflectionTarget()))
    CialloEngine(environment).execute(
      "print(@host.value)\nprint(@host.double(3))\n@host.value set 9\nprint(@host.value)",
    )
    assertEquals(listOf("7", "6", "9"), lines)
  }

  @Test
  fun fullSandboxResolvesStaticFields() {
    val lines = mutableListOf<String>()
    val environment =
      CialloEnvironment(
        output = { lines += it },
        sandbox = CialloSandbox(allowJvmAccess = true, allowedPackages = listOf("peroxicore.ciallo")),
      )
    CialloEngine(environment).execute("print(@CialloStaticTarget.value)")
    assertEquals(listOf("5"), lines)
  }

  /*
  @Test
  fun exampleProgramRuns() {
    val source =
      """
      target @Blocks.duo
      world {
        size(11, 11)
        fill @Blocks.green
      }
      @center mount(at(5, 5))

      frame(1s) {
        build @Blocks.duo at @center
        label "@blocks.duo.lable1" at(@center.add(1, 0)) duration 5s
      }
      """
    val (lines, scene) = runWithSymbols(source)
    assertEquals(listOf("@blocks.duo.lable1"), lines)
    assertEquals(CialloValue.Str("duo"), scene.target)
    assertEquals(11, scene.world?.width)
    assertEquals(11, scene.world?.height)
    assertEquals(CialloValue.Str("green"), scene.world?.fill)
    assertEquals(1, scene.builds.size)
    assertEquals(CialloValue.Point(5f, 5f), scene.builds[0].position)
    assertEquals(1, scene.labels.size)
    assertEquals(CialloValue.Point(6f, 5f), scene.labels[0].position)
    assertEquals(CialloValue.TimeValue(5.0, CialloTimeUnit.Second), scene.labels[0].duration)
    assertEquals(1, scene.frames.size)
    assertEquals(CialloValue.TimeValue(1.0, CialloTimeUnit.Second), scene.frames[0].time)
  }*/

  @Test
  fun customBuiltinCanBeRegistered() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    environment.native("twice") { context, args ->
      CialloValue.IntValue(context.int(args[0], "twice") * 2)
    }
    CialloEngine(environment).execute("print(twice(21))")
    assertEquals(listOf("42"), lines)
  }
}
