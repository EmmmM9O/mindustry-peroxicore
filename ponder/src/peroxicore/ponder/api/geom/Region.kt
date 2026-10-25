package peroxicore.ponder.api.geom

/** A rectangular tile region, inclusive on both corners. */
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
