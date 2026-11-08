package peroxicore.struct

import arc.math.geom.*

/**
 * 把 [RotatedRect] 直接放进 [QuadTree2] 的对象包装。
 *
 * [QuadTree2] 的 [QuadTree2.QuadTreeObject.hitbox] 只接受轴对齐矩形，所以这里写入的是旋转矩形的外接盒。
 * 外接盒会带来误报（矩形旋转 45 度时，外接盒面积约是实际的两倍），
 * 需要更精确时可以用 [RotatedRect.tileBounds] 拆成多段分别插入。
 *
 * 所有坐标都保存在一个可变的 [RotatedRect] 里，[hitbox] 不产生分配。
 */
class RotatedHitbox(val shape: RotatedRect = RotatedRect()) : QuadTree2.QuadTreeObject {
  constructor(x: Float, y: Float, width: Float, height: Float, rotation: Float) : this(RotatedRect(x, y, width, height, rotation))

  /** 写入外接盒；不分配。 */
  override fun hitbox(out: Rect) {
    shape.bounds(out)
  }

  /** 精确判定：给定点在旋转矩形内。 */
  fun contains(x: Float, y: Float): Boolean = shape.contains(x, y)

  /** 精确判定：与另一个旋转矩形相交。 */
  fun overlaps(other: RotatedRect): Boolean = shape.overlaps(other)

  /** 精确判定：与轴对齐矩形相交。 */
  fun overlaps(rect: Rect): Boolean = shape.overlaps(rect)

  override fun toString(): String = "RotatedHitbox(" + shape + ")"
}
