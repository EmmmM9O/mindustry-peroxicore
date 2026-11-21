package peroxicore.ciallo

import peroxicore.ciallo.value.*
import kotlin.test.*

/**
 * Time literals: `1s` is 60 game ticks, and `t` is the tick unit Ponder uses.
 */
class CialloTimeUnitTest {
  private fun run(source: String): List<String> {
    val lines = mutableListOf<String>()
    CialloEngine(CialloEnvironment(output = { lines += it })).execute(source)
    return lines
  }

  @Test
  fun tickLiteralParses() {
    val value = CialloEngine().execute("60t")
    assertEquals(CialloTime(60.0, CialloTimeUnit.Tick), value)
  }

  @Test
  fun secondsConvertToSixtyTicks() {
    assertEquals(listOf("60.0"), run("@t local 1s\nprint(@t.ticks)"))
    assertEquals(listOf("60.0"), run("@t local 60t\nprint(@t.ticks)"))
    assertEquals(listOf("3600.0"), run("@t local 1m\nprint(@t.ticks)"))
  }

  @Test
  fun tickUnitIsTheSmallestUnit() {
    assertEquals(1.0, CialloTimeUnit.Tick.ticks)
    assertEquals(60.0, CialloTimeUnit.Second.ticks)
    assertEquals(3_600.0, CialloTimeUnit.Minute.ticks)
  }

  @Test
  fun toTicksMemberAndPropertyAgree() {
    assertEquals(listOf("30.0", "30.0"), run("@t local 30t\nprint(@t.ticks)\nprint(@t.toTicks())"))
  }
}
