package peroxicore.ciallo.host

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*
import kotlin.test.*

/**
 * The host binding layer: typed overloads, time-to-float coercion and deferred
 * actions (`build(block) at(x, y)`).
 */
class CialloHostTest {
  private class Point(
    val x: Int,
    val y: Int,
  ) {
    override fun equals(other: Any?): Boolean = other is Point && other.x == x && other.y == y

    override fun hashCode(): Int = x * 31 + y
  }

  private class Harness {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    val engine = CialloEngine(environment)
    val binder = CialloHostBinder(environment.nativeTarget())

    init {
      binder.alias(Point::class.java, "Point")
      binder.positionFunction = "at"
      binder.free("at", listOf(CialloParams.int("x"), CialloParams.int("y"))) { _, args ->
        Point(args[0] as Int, args[1] as Int)
      }
      binder.free("at", listOf(CialloParams.float("x"), CialloParams.float("y"))) { _, args ->
        Point((args[0] as Float).toInt(), (args[1] as Float).toInt())
      }
    }

    fun install() = binder.install()

    fun run(source: String): CialloValue = engine.execute(source)
  }

  @Test
  fun atDispatchesByExactType() {
    val harness = Harness()
    harness.install()
    val point = harness.run("at(4, 5)") as CialloHostValue
    assertEquals(Point(4, 5), point.value)
    // Floats choose the other overload instead of failing.
    assertEquals(Point(1, 2), (harness.run("at(1f, 2f)") as CialloHostValue).value)
  }

  @Test
  fun memberReadsAndCallsReachTheHost() {
    val harness = Harness()
    harness.binder.member(Point::class.java, "offset", listOf(CialloParams.int("dx"))) { _, receiver, args ->
      val point = receiver as Point
      Point(point.x + args[0] as Int, point.y)
    }
    harness.install()
    assertEquals(Point(6, 5), (harness.run("at(4, 5) offset(2)") as CialloHostValue).value)
  }

  @Test
  fun deferredActionRunsAtThePosition() {
    val harness = Harness()
    val built = mutableListOf<String>()
    harness.binder.action(Point::class.java, "build", listOf(CialloParams.str("name"))) { _, receiver, args ->
      val point = receiver as Point
      built += "${args[0]} at ${point.x},${point.y}"
    }
    harness.install()

    // `build "duo" at(4, 5)`: the action is deferred until `at`.
    harness.run("build \"duo\" at(4, 5)")
    assertEquals(listOf("duo at 4,5"), built)

    // The receiver form runs immediately at that point.
    harness.run("at(1, 2) build(\"wall\")")
    assertEquals(listOf("duo at 4,5", "wall at 1,2"), built)
  }

  @Test
  fun deferredActionAcceptsConfigModifier() {
    val harness = Harness()
    val built = mutableListOf<String>()
    harness.binder.action(
      Point::class.java,
      "build",
      listOf(
        CialloParams.str("name"),
        CialloParams.any("config", default = { null }),
      ),
    ) { _, receiver, args ->
      val point = receiver as Point
      built += "${args[0]}(${args[1]}) at ${point.x},${point.y}"
    }
    harness.install()

    harness.run("build \"duo\" config \"copper\" at(4, 5)")
    assertEquals(listOf("duo(copper) at 4,5"), built)
  }

  @Test
  fun floatParametersAcceptTimeInTicks() {
    val harness = Harness()
    val seen = mutableListOf<Float>()
    harness.binder.free("wait", listOf(CialloParams.float("duration"))) { _, args ->
      seen += args[0] as Float
    }
    harness.install()

    harness.run("wait 1s")
    harness.run("wait 60t")
    harness.run("wait 2")
    // 1s and 60t are both 60 ticks; the Int is coerced.
    assertEquals(listOf(60f, 60f, 2f), seen)
  }
}
