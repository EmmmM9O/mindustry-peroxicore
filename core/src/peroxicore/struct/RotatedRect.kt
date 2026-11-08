package peroxicore.struct

import arc.math.*
import arc.math.geom.*
import arc.struct.*

/**
 * 带旋转角的矩形（有向包围盒 / OBB）。
 *
 * 这个类本身是可变的，内部不保存任何中间结果：所有计算结果都写进调用方提供的 [Rect] / [Vec2]，
 * 因此查询、命中判定、包围盒计算都不会产生分配。
 *
 * 两种描述方式：
 * - 中心 + 尺寸 + 角度（[set]），最常用；
 * - 左下角 + 尺寸 + 角度（[setFromCorner]），与 [Rect] 的语义一致。
 *
 * 角度单位为度，逆时针为正，与 Arc 的约定一致。
 *
 * 命名约定：写入型方法都接受调用方持有的输出对象，复用它即可避免分配。
 */
class RotatedRect(
  var x: Float,
  var y: Float,
  var width: Float,
  var height: Float,
  var rotation: Float,
) {
  constructor() : this(0f, 0f, 0f, 0f, 0f)

  constructor(rect: Rect) : this(rect.x + rect.width / 2f, rect.y + rect.height / 2f, rect.width, rect.height, 0f)

  constructor(other: RotatedRect) : this(other.x, other.y, other.width, other.height, other.rotation)

  fun set(cx: Float, cy: Float, width: Float, height: Float, rotation: Float): RotatedRect = apply {
    this.x = cx
    this.y = cy
    this.width = width
    this.height = height
    this.rotation = rotation
  }

  fun set(other: RotatedRect): RotatedRect = apply {
    x = other.x
    y = other.y
    width = other.width
    height = other.height
    rotation = other.rotation
  }

  fun set(rect: Rect): RotatedRect = set(rect.x + rect.width / 2f, rect.y + rect.height / 2f, rect.width, rect.height, 0f)

  fun setFromCorner(cornerX: Float, cornerY: Float, width: Float, height: Float, rotation: Float): RotatedRect =
    set(cornerX + width / 2f, cornerY + height / 2f, width, height, rotation)

  fun moveBy(dx: Float, dy: Float): RotatedRect = apply {
    x += dx
    y += dy
  }

  fun rotateBy(degrees: Float): RotatedRect = apply {
    rotation = Mathf.mod(rotation + degrees, 360f)
  }

  fun setSize(width: Float, height: Float): RotatedRect = apply {
    this.width = width
    this.height = height
  }

  val halfWidth: Float get() = width / 2f
  val halfHeight: Float get() = height / 2f
  val radius: Float get() = Mathf.sqrt(width * width + height * height) / 2f
  val area: Float get() = width * height

  fun corner(index: Int, out: Vec2): Vec2 {
    val hw = halfWidth
    val hh = halfHeight
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    val lx = if (index == 0 || index == 1) -hw else hw
    val ly = if (index == 1 || index == 2) hh else -hh
    return out.set(x + lx * c - ly * s, y + lx * s + ly * c)
  }

  fun cornersInto(out: FloatArray, offset: Int = 0): FloatArray {
    val hw = halfWidth
    val hh = halfHeight
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    var i = offset
    for (k in 0 until 4) {
      val lx = if (k == 0 || k == 1) -hw else hw
      val ly = if (k == 1 || k == 2) hh else -hh
      out[i++] = x + lx * c - ly * s
      out[i++] = y + lx * s + ly * c
    }
    return out
  }

  fun bounds(out: Rect): Rect {
    val c = kotlin.math.abs(Mathf.cosDeg(rotation))
    val s = kotlin.math.abs(Mathf.sinDeg(rotation))
    val hw = halfWidth
    val hh = halfHeight
    val bw = 2f * (hw * c + hh * s)
    val bh = 2f * (hw * s + hh * c)
    return out.set(x - bw / 2f, y - bh / 2f, bw, bh)
  }

  fun center(out: Vec2): Vec2 = out.set(x, y)

  fun axisX(out: Vec2): Vec2 = out.set(Mathf.cosDeg(rotation), Mathf.sinDeg(rotation))

  fun axisY(out: Vec2): Vec2 = out.set(-Mathf.sinDeg(rotation), Mathf.cosDeg(rotation))

  fun toLocal(px: Float, py: Float, out: Vec2): Vec2 {
    val dx = px - x
    val dy = py - y
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    return out.set(dx * c + dy * s, -dx * s + dy * c)
  }

  fun toWorld(lx: Float, ly: Float, out: Vec2): Vec2 {
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    return out.set(x + lx * c - ly * s, y + lx * s + ly * c)
  }

  fun contains(px: Float, py: Float): Boolean {
    val local = toLocal(px, py, tmpVec)
    return kotlin.math.abs(local.x) <= halfWidth + EPS && kotlin.math.abs(local.y) <= halfHeight + EPS
  }

  fun contains(point: Vec2): Boolean = contains(point.x, point.y)

  fun overlaps(other: RotatedRect): Boolean {
    val a = bounds(tmpRect)
    val b = other.bounds(tmpRect2)
    if (!a.overlaps(b)) return false
    if (separatedOn(other, axisX(tmpVec))) return false
    if (separatedOn(other, axisY(tmpVec))) return false
    if (separatedOn(other, other.axisX(tmpVec))) return false
    if (separatedOn(other, other.axisY(tmpVec))) return false
    return true
  }

  private fun separatedOn(other: RotatedRect, axis: Vec2): Boolean {
    val ax = axis.x
    val ay = axis.y
    val centerDistance = (other.x - x) * ax + (other.y - y) * ay
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    val selfRadius = kotlin.math.abs(halfWidth * (c * ax + s * ay)) + kotlin.math.abs(halfHeight * (-s * ax + c * ay))
    val oc = Mathf.cosDeg(other.rotation)
    val os = Mathf.sinDeg(other.rotation)
    val otherRadius = kotlin.math.abs(other.halfWidth * (oc * ax + os * ay)) + kotlin.math.abs(other.halfHeight * (-os * ax + oc * ay))
    return kotlin.math.abs(centerDistance) > selfRadius + otherRadius + EPS
  }

  fun overlaps(rect: Rect): Boolean {
    val a = bounds(tmpRect)
    if (!a.overlaps(rect)) return false

    // 轴对齐矩形等价于 rotation = 0；只需在两个矩形的本地轴上检验
    return !separatedOnAxis(
      rect.x + rect.width / 2f, rect.y + rect.height / 2f, rect.width / 2f, rect.height / 2f, 0f,
      axisX(tmpVec),
    ) &&
      !separatedOnAxis(
        rect.x + rect.width / 2f, rect.y + rect.height / 2f, rect.width / 2f, rect.height / 2f, 0f,
        axisY(tmpVec),
      )
  }

  /** 分离轴检验的通用形式：另一个矩形用中心、半尺寸、角度描述。 */
  private fun separatedOnAxis(
    otherX: Float,
    otherY: Float,
    otherHalfWidth: Float,
    otherHalfHeight: Float,
    otherRotation: Float,
    axis: Vec2,
  ): Boolean {
    val ax = axis.x
    val ay = axis.y
    val centerDistance = (otherX - x) * ax + (otherY - y) * ay
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    val selfRadius = kotlin.math.abs(halfWidth * (c * ax + s * ay)) + kotlin.math.abs(halfHeight * (-s * ax + c * ay))
    val oc = Mathf.cosDeg(otherRotation)
    val os = Mathf.sinDeg(otherRotation)
    val otherRadius = kotlin.math.abs(otherHalfWidth * (oc * ax + os * ay)) + kotlin.math.abs(otherHalfHeight * (-os * ax + oc * ay))
    return kotlin.math.abs(centerDistance) > selfRadius + otherRadius + EPS
  }

  fun overlapsCircle(circleX: Float, circleY: Float, circleRadius: Float): Boolean {
    val local = toLocal(circleX, circleY, tmpVec)
    val dx = Mathf.maxZero(kotlin.math.abs(local.x) - halfWidth)
    val dy = Mathf.maxZero(kotlin.math.abs(local.y) - halfHeight)
    return dx * dx + dy * dy <= circleRadius * circleRadius + EPS
  }

  fun hitbox(out: Rect) {
    bounds(out)
  }

  fun tileBounds(steps: Int, out: Seq<Rect>) {
    require(steps >= 1) { "steps must be >= 1: " + steps }
    val pieceWidth = width / steps
    val c = Mathf.cosDeg(rotation)
    val s = Mathf.sinDeg(rotation)
    val hw = halfWidth
    val hh = halfHeight
    for (i in 0 until steps) {
      val lx0 = -hw + i * pieceWidth
      val lx1 = lx0 + pieceWidth
      var minX = Float.POSITIVE_INFINITY
      var minY = Float.POSITIVE_INFINITY
      var maxX = Float.NEGATIVE_INFINITY
      var maxY = Float.NEGATIVE_INFINITY
      for (cornerY in 0 until 2) {
        for (cornerX in 0 until 2) {
          val lx = if (cornerX == 0) lx0 else lx1
          val ly = if (cornerY == 0) -hh else hh
          val wx = x + lx * c - ly * s
          val wy = y + lx * s + ly * c
          if (wx < minX) minX = wx
          if (wx > maxX) maxX = wx
          if (wy < minY) minY = wy
          if (wy > maxY) maxY = wy
        }
      }
      out.add(Rect(minX, minY, maxX - minX, maxY - minY))
    }
  }

  fun mergeInto(out: Rect): Rect {
    cornersInto(tmpCorners)
    for (i in 0 until 8 step 2) out.merge(tmpCorners[i], tmpCorners[i + 1])
    return out
  }

  fun toRect(): Rect = bounds(Rect())

  override fun toString(): String = "RotatedRect(" + x + ", " + y + ", " + width + "x" + height + " @" + rotation + ")"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RotatedRect) return false
    return x == other.x && y == other.y && width == other.width && height == other.height && rotation == other.rotation
  }

  override fun hashCode(): Int {
    var result = x.hashCode()
    result = 31 * result + y.hashCode()
    result = 31 * result + width.hashCode()
    result = 31 * result + height.hashCode()
    result = 31 * result + rotation.hashCode()
    return result
  }

  companion object {
    const val EPS: Float = 0.0001f
    private val tmpCorners = FloatArray(8)
    private val tmpVec = Vec2()
    private val tmpRect = Rect()
    private val tmpRect2 = Rect()
  }
}
