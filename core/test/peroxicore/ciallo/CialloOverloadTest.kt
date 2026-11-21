package peroxicore.ciallo

import peroxicore.ciallo.value.*
import kotlin.test.*

/** Strict overload dispatch for natives, free functions and member tables. */
class CialloOverloadTest {
  private fun environment(
    output: MutableList<String> = mutableListOf(),
  ): CialloEnvironment = CialloEnvironment(output = { output += it })

  private fun run(environment: CialloEnvironment, source: String): CialloValue =
    CialloEngine(environment).execute(source)

  /** The exact shape from the docs: `at` with ints and with floats are different results. */
  private fun atEnvironment(output: MutableList<String>): CialloEnvironment =
    environment(output).apply {
      native("at", listOf(CialloType.Int, CialloType.Int)) { _, _ -> CialloStr("point") }
      native("at", listOf(CialloType.Float, CialloType.Float)) { _, _ -> CialloStr("vec2") }
    }

  @Test
  fun nativeOverloadsPickByExactType() {
    val lines = mutableListOf<String>()
    val environment = atEnvironment(lines)
    run(environment, "print(at(1, 2))")
    run(environment, "print(at(1f, 2f))")
    assertEquals(listOf("point", "vec2"), lines)
  }

  @Test
  fun nativeOverloadRejectsMixedTypes() {
    val error =
      assertFailsWith<CialloRuntimeException> {
        run(atEnvironment(mutableListOf()), "at(1, 2f)")
      }
    assertTrue("has no overload for (Int, Float)" in error.message!!, error.message)
    assertTrue("(Int, Int)" in error.message!!, error.message)
  }

  @Test
  fun nativeOverloadResolvesSymbolsBeforeMatching() {
    val lines = mutableListOf<String>()
    val environment = environment(lines)
    environment.native("at", listOf(CialloType.Int, CialloType.Int)) { _, _ -> CialloStr("point") }
    run(environment, "@x local 1\n@y local 2\nprint(at(@x, @y))")
    assertEquals(listOf("point"), lines)
  }

  @Test
  fun untypedOverloadAnswersWhenNoStrictOneMatches() {
    val lines = mutableListOf<String>()
    val environment = environment(lines)
    environment.native("describe", listOf(CialloType.Int)) { _, _ -> CialloStr("int") }
    environment.native("describe") { _, args -> CialloStr("other:" + args.size) }
    run(environment, "print(describe(1))")
    run(environment, "print(describe(\"x\"))")
    run(environment, "print(describe(1, 2))")
    assertEquals(listOf("int", "other:1", "other:2"), lines)
  }

  @Test
  fun freeFunctionOverloadsDoNotReplaceEachOther() {
    val lines = mutableListOf<String>()
    val environment = environment(lines)
    environment.freeFunction("pick", listOf(CialloType.Int)) { _, _ -> CialloStr("int") }
    environment.freeFunction("pick", listOf(CialloType.Float)) { _, _ -> CialloStr("float") }
    run(environment, "print(pick(1))")
    run(environment, "print(pick(1f))")
    assertEquals(listOf("int", "float"), lines)
  }

  @Test
  fun freeFunctionOverloadKeepsExistingComponent() {
    val lines = mutableListOf<String>()
    val environment = environment(lines)
    // `not` is a builtin component; a typed overload takes precedence for its type, while other
    // argument types still fall back to the builtin.
    environment.freeFunction("not", listOf(CialloType.Int)) { _, _ -> CialloStr("int-not") }
    run(environment, "print(not(1))")
    run(environment, "print(not(false))")
    assertEquals(listOf("int-not", "true"), lines)
  }

  @Test
  fun freeFunctionOverloadMismatchIsReported() {
    val environment = environment()
    environment.freeFunction("pick", listOf(CialloType.Int)) { _, _ -> CialloStr("int") }
    val error = assertFailsWith<CialloRuntimeException> { run(environment, "pick(1f)") }
    assertTrue("has no overload for (Float)" in error.message!!, error.message)
  }

  @Test
  fun memberTableOverloadsPickByExactType() {
    val lines = mutableListOf<String>()
    val environment = environment(lines)
    environment.symbol("c", object : CialloValue {
      override val kind: CialloKind get() = CialloKind.Obj

      override val members: CialloMembers =
        memberTable {
          method("mix", listOf(CialloType.Int, CialloType.Int)) { _, _, _ -> CialloStr("int-int") }
          method("mix", listOf(CialloType.Str, CialloType.Int)) { _, _, _ -> CialloStr("str-int") }
        }

      override fun display(): String = "<c>"
    })
    run(environment, "print(c.mix(1, 2))")
    run(environment, "print(c.mix(\"a\", 2))")
    assertEquals(listOf("int-int", "str-int"), lines)
  }

  @Test
  fun memberTableOverloadMismatchIsReported() {
    val environment = environment()
    environment.symbol("c", object : CialloValue {
      override val kind: CialloKind get() = CialloKind.Obj

      override val members: CialloMembers =
        memberTable {
          method("mix", listOf(CialloType.Int, CialloType.Int)) { _, _, _ -> CialloStr("int-int") }
        }

      override fun display(): String = "<c>"
    })
    val error = assertFailsWith<CialloRuntimeException> { run(environment, "c.mix(1, \"a\")") }
    assertTrue("has no overload for (Int, String)" in error.message!!, error.message)
  }

  @Test
  fun untypedMemberAnswersWhenStrictOverloadMisses() {
    val lines = mutableListOf<String>()
    val environment = environment(lines)
    environment.symbol("c", object : CialloValue {
      override val kind: CialloKind get() = CialloKind.Obj

      override val members: CialloMembers =
        memberTable {
          method("mix", listOf(CialloType.Int, CialloType.Int)) { _, _, _ -> CialloStr("int-int") }
          method("mix") { _, _, args -> CialloStr("general:" + args.size) }
        }

      override fun display(): String = "<c>"
    })
    run(environment, "print(c.mix(1, 2))")
    run(environment, "print(c.mix(1, 2, 3))")
    assertEquals(listOf("int-int", "general:3"), lines)
  }

  @Test
  fun setWithTwoArgumentsReachesArrayMember() {
    val lines = mutableListOf<String>()
    run(environment(lines), "@a local [1, 2]\n@a.set(0, 9)\nprint @a")
    assertEquals(listOf("[9, 2]"), lines)
  }

  @Test
  fun setWithOneArgumentStillAssignsVariable() {
    val lines = mutableListOf<String>()
    run(environment(lines), "@x local 1\n@x set(2)\nprint @x")
    assertEquals(listOf("2"), lines)
  }

  @Test
  fun setWithOneBareValueStillAssignsVariable() {
    val lines = mutableListOf<String>()
    run(environment(lines), "@x local 1\n@x set 2\nprint @x")
    assertEquals(listOf("2"), lines)
  }
}
