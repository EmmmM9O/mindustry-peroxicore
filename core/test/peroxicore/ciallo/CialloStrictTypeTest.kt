package peroxicore.ciallo

import kotlin.test.*

class CialloStrictTypeTest {
  private fun engine(): CialloEngine = CialloEngine(CialloEnvironment(output = { }))

  /*

  @Test
  fun atWithIntsReturnsPoint() {
    val result = engine().execute("at(1, 2)")
    assertIs<CialloValue.Point>(result)
    assertEquals(1f, result.x)
    assertEquals(2f, result.y)
  }

  @Test
  fun atWithFloatsReturnsVec2() {
    val result = engine().execute("at(1f, 2f)")
    assertIs<CialloValue.Vec2>(result)
    assertEquals(1f, result.x)
    assertEquals(2f, result.y)
  }

  @Test
  fun atRejectsMixedTypes() {
    assertFailsWith<CialloRuntimeException> {
      engine().execute("at(1, 2f)")
    }
  }

  @Test
  fun atResolvesSymbolsBeforeMatching() {
    val result = engine().execute("@x local 1\n@y local 2\nat(@x, @y)")
    assertIs<CialloValue.Point>(result)
  }

  @Test
  fun strictHostOverloadsAreEnforced() {
    val environment = CialloEnvironment(output = { })
    environment.native("onlyInt", listOf(CialloType.Int)) { context, args ->
      CialloValue.IntValue(context.int(args[0], "onlyInt"))
    }
    assertEquals(CialloValue.IntValue(3), CialloEngine(environment).execute("onlyInt(3)"))
    assertFailsWith<CialloRuntimeException> {
      CialloEngine(environment).execute("onlyInt(3f)")
    }
  }

  @Test
  fun vec2EntryAndMethods() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    CialloEngine(environment).execute("label(at(1f, 2f) add(1f, 1f))")
    assertEquals(listOf("Vec2(2.0, 3.0)"), lines)
  }
  */
}
