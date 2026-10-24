package peroxicore.util

/**
 * 一次批量修改的脏矩形区域（闭区间边界），由 [TileChunk] 持有并复用。
 *
 * 只记录包围盒，不记录每个脏 cell；这对局部修改足够快，也不会产生大量对象。
 */
class DirtyRegion internal constructor() {
  var minX: Int = 0
    internal set

  var minY: Int = 0
    internal set

  var maxX: Int = -1
    internal set

  var maxY: Int = -1
    internal set

  val isEmpty: Boolean
    get() = maxX < minX || maxY < minY

  val width: Short
    get() = if (isEmpty) 0.toShort() else (maxX - minX + 1).toShort()

  val height: Short
    get() = if (isEmpty) 0.toShort() else (maxY - minY + 1).toShort()

  val area: Int
    get() = width.toInt() * height.toInt()

  internal fun clear() {
    minX = 0
    minY = 0
    maxX = -1
    maxY = -1
  }

  internal fun include(x: Int, y: Int) {
    if (isEmpty) {
      minX = x
      maxX = x
      minY = y
      maxY = y
      return
    }
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }

  internal fun include(x: Int, y: Int, width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    include(x, y)
    include(x + width - 1, y + height - 1)
  }

  fun contains(x: Int, y: Int): Boolean =
    !isEmpty && x >= minX && x <= maxX && y >= minY && y <= maxY

  override fun toString(): String =
    if (isEmpty) "DirtyRegion(empty)" else "DirtyRegion($minX,$minY..$maxX,$maxY)"
}
