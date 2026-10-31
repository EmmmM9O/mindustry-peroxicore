package peroxicore.ciallo

import kotlin.test.*

class CialloMathTest {
  private fun runLines(source: String): List<String> {
    val lines = mutableListOf<String>()
    CialloEngine(CialloEnvironment(output = { lines += it })).execute(source)
    return lines
  }

  @Test
  fun arithmeticPrecedence() {
    val source =
      """
      print(1 + 2 * 3)
      print((1 + 2) * 3)
      print(10 - 2 - 3)
      print(2 * 3 + 4 * 5)
      print(7 % 3)
      print(10 / 2)
      """.trimIndent()
    assertEquals(listOf("7", "9", "5", "26", "1", "5"), runLines(source))
  }

  @Test
  fun comparisons() {
    val source =
      """
      print(1 + 2 == 3)
      print(1 + 2 != 3)
      print(1 < 2)
      print(2 <= 2)
      print(3 > 2)
      print(3 >= 4)
      print(1 == 1.0)
      """.trimIndent()
    assertEquals(
      listOf("true", "false", "true", "true", "true", "false", "true"),
      runLines(source),
    )
  }

  @Test
  fun unaryOperatorsAndStringAddition() {
    val source =
      """
      print(-1 + 2)
      print(!false)
      print(!"")
      print("a" + 1)
      """.trimIndent()
    assertEquals(listOf("1", "true", "true", "a1"), runLines(source))
  }

  @Test
  fun expressionsUseVariablesAndBuiltins() {
    val source =
      """
      @x local 2
      print(@x * 3 + 1)
      @x set(@x + 5)
      print(@x)
      ifTrue(1 + 2 == 3) { print "yes" }
      """.trimIndent()
    assertEquals(listOf("7", "7", "yes"), runLines(source))
  }

  @Test
  fun mixedIntFloatResultIsFloat() {
    assertEquals(listOf("3.5"), runLines("print(1 + 2.5)"))
  }
}
