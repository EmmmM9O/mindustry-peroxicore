package peroxicore.ponder.api.dsl

import arc.math.geom.*
import mindustry.*
import kotlin.math.*

@PonderDslMarker
interface PositionScope {
  typealias Point = Point2

  typealias Vec = Vec2

  typealias Rect = arc.math.geom.Rect

  class Region(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
  ) {
    constructor(
      xRange: IntRange,
      yRange: IntRange,
    ) : this(xRange.first, yRange.first, xRange.last, yRange.last)

    val xRange
      get() = x1..x2

    val yRange
      get() = y1..y2
  }

  infix fun Int.at(y: Int) = Point(this, y)

  infix fun IntRange.by(yRange: IntRange) = Region(this, yRange)

  infix fun Point.by(other: Point) =
    (if (x <= other.x) x..other.x else other.x..x) by (if (y <= other.y) y..other.y else other.y..y)

  infix fun ClosedFloatingPointRange<Float>.by(yRange: ClosedFloatingPointRange<Float>) =
    Rect(start, yRange.start, endInclusive - start, yRange.endInclusive - yRange.start)

  infix fun Vec.by(other: Vec) =
    (if (x <= other.x) x..other.x else other.x..x) by (if (y <= other.y) y..other.y else other.y..y)

  fun Vec.size(
    width: Float,
    height: Float,
  ) = Rect(x, y, width, height)

  fun Vec.center(
    width: Float,
    height: Float,
  ) = Rect().setCentered(x, y, width, height)

  fun Point.around(size: Int): Region {
    val half = (size - 1) / 2
    return ((x - half)..(x + half)) by ((y - half)..(y + half))
  }

  fun Point.around3() = this.around(3)

  fun Point.focusRect(range: Int) = unit().center(range.unit(), range.unit())

  infix fun Point.area(size: Int) = this.around(size)

  fun Int.unit(): Float = Vars.tilesize * this.toFloat()

  fun Float.world(): Int = (this / Vars.tilesize).roundToInt()

  fun Region.unit() = (x1.unit() at y1.unit()) by (x2.unit() at y2.unit())

  fun Region.camera() = unit().camera()

  fun Rect.camera() = move(-4f, -4f)

  infix fun Float.at(y: Float) = Vec(this, y)

  fun Point.unit() = x.unit() at y.unit()

  fun Vec.world() = x.world() at y.world()

  fun Pair<Float, Float>.vec() = first at second

  fun Vec.pair() = x to y

  fun toMax(
    width: Float,
    height: Float,
    max: Float,
  ): Pair<Float, Float> {
    val scale = if (width > height) max / width else max / height
    return width * scale to height * scale
  }

  fun toMin(
    width: Float,
    height: Float,
    min: Float,
  ): Pair<Float, Float> {
    val scale = if (width > height) min / height else min / width
    return width * scale to height * scale
  }

  @JvmInline
  value class PointSpec<Res>(
    val spec: Point.() -> Res,
  )

  fun <Res> PointSpec<Res>.at(point: Point) = point.run(spec)

  fun <Res> PointSpec<Res>.at(
    x: Int,
    y: Int,
  ) = (x at y).run(spec)
}
