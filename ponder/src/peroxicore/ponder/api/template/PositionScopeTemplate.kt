package peroxicore.ponder.api.template

import arc.math.geom.*
import mindustry.*
import peroxicore.annotations.*
import peroxicore.ponder.api.dsl.*
import kotlin.math.*

@ApiTemplate(
  targetPackage = "peroxicore.ponder.api.dsl",
  name = "PositionScope",
  annotations = ["peroxicore.ponder.api.dsl.PonderDslMarker"],
)
@PonderDslMarker
interface PositionScopeTemplate {
  @Actionable(
    javaName = "Tile",
  )
  typealias Point = Point2

  @Actionable(
    javaName = "Vec",
  )
  typealias Vec = Vec2

  @Actionable(
    javaName = "Region",
  )
  typealias Region = peroxicore.ponder.api.geom.Region

  typealias Rect = arc.math.geom.Rect

  @ActionEnd
  fun at(
    x: Int,
    y: Int,
  ): Point = Point(x, y)

  @ActionEnd
  fun at(
    x: Float,
    y: Float,
  ): Vec = Vec(x, y)

  fun region(
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
  ): Region = Region(x1, y1, x2, y2)

  fun region(
    xRange: IntRange,
    yRange: IntRange,
  ): Region = Region(xRange, yRange)

  fun Point.around(size: Int): Region {
    val half = (size - 1) / 2
    return Region((x - half), (y - half), (x + half), (y + half))
  }

  fun Point.around3(): Region = around(3)

  fun Point.focusRect(range: Int): Rect = unit().center(range.unit(), range.unit())

  fun Point.area(size: Int): Region = around(size)

  fun Int.unit(): Float = Vars.tilesize * this.toFloat()

  fun Float.world(): Int = (this / Vars.tilesize).roundToInt()

  fun Region.unit(): Rect =
    Vec(x1.unit(), y1.unit()).to(Vec(x2.unit(), y2.unit()))

  fun Region.camera(): Rect = unit().camera()

  fun Rect.camera(): Rect = move(-4f, -4f)!!

  fun Point.unit(): Vec = Vec(x.unit(), y.unit())

  fun Vec.world(): Point = at(x.world(), y.world())

  fun Pair<Float, Float>.vec(): Vec = Vec(first, second)

  fun Vec.pair(): Pair<Float, Float> = x to y

  fun Vec.size(
    width: Float,
    height: Float,
  ): Rect = Rect(x, y, width, height)

  fun Vec.center(
    width: Float,
    height: Float,
  ): Rect = Rect().setCentered(x, y, width, height)!!

  fun Vec.to(other: Vec): Rect =
    Rect(
      min(x, other.x),
      min(y, other.y),
      abs(other.x - x),
      abs(other.y - y),
    )

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
}
