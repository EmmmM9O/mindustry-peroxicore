package peroxicore.ciallo

import peroxicore.ciallo.host.*
import kotlin.test.*

/**
 * `.member` continues the previous segment, so `at(5, 5).showConfig()` is the long form of
 * `at(5, 5) showConfig()`, and literals can be used as receivers (`5s.millis`).
 */
class CialloDotMemberTest {
  private class Point(
    val x: Int,
    val y: Int,
  )

  private fun run(source: String): List<String> {
    val lines = mutableListOf<String>()
    CialloEngine(CialloEnvironment(output = { lines += it })).execute(source)
    return lines
  }

  @Test
  fun dotAndSpaceMemberFormsAgree() {
    assertEquals(run("print([1, 2].size)"), run("print([1, 2] size)"))
    assertEquals(listOf("2"), run("print([1, 2].size)"))
  }

  @Test
  fun dotMemberWorksAfterACall() {
    assertEquals(listOf("5"), run("@a local [1, 2, 3, 4, 5]\nprint(@a.size)"))
    assertEquals(listOf("1"), run("@a local [1, 2, 3]\nprint(@a.first)"))
  }

  @Test
  fun dotMemberWorksOnLiterals() {
    assertEquals(listOf("5000"), run("@t local 5s\nprint(@t.millis)"))
    // The literal itself may be the receiver, without binding it to a variable first.
    assertEquals(listOf("hi"), run("print(\"Hi\".lower())"))
    assertEquals(listOf("3"), run("print(1.add(2))"))
    assertEquals(listOf("2"), run("print([1, 2].size)"))
  }

  @Test
  fun dotAfterCallResultChains() {
    // `at(5, 5).showConfig()` — the shape the user asked about, with a host binding for `at`.
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    val binder = CialloHostBinder(environment.nativeTarget())
    binder.alias(Point::class.java, "Point")
    binder.positionFunction = "at"
    binder.free(
      "at",
      listOf(CialloParams.int("x"), CialloParams.int("y")),
    ) { _, args ->
      Point(args[0] as Int, args[1] as Int)
    }
    binder.member(Point::class.java, "showConfig", listOf()) { _, receiver, _ ->
      val point = receiver as Point
      lines += "showConfig(${point.x},${point.y})"
    }
    binder.install()

    CialloEngine(environment).execute("at(5, 5).showConfig()")
    assertEquals(listOf("showConfig(5,5)"), lines)
  }

  @Test
  fun spacedDotIsNormalised() {
    assertEquals(
      CialloFormatter.format("print([1, 2].size)\n"),
      CialloFormatter.format("print([1, 2] . size)\n"),
    )
  }

  @Test
  fun leadingDotWithoutReceiverFails() {
    assertFailsWith<CialloException> {
      // `.size` at the head of a chain has no receiver; it must not silently become a name.
      CialloEngine().execute("@x local [1]\nprint(.size)")
    }
  }

  @Test
  fun dotFormattedAsCanonical() {
    val source = "at(5, 5).showConfig()\n"
    assertEquals(source, CialloFormatter.format(source))
    // The space form formats to the dot form.
    assertEquals(source, CialloFormatter.format("at(5, 5) . showConfig()\n"))
  }
}
