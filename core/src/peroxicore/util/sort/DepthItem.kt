package peroxicore.util.sort

/**
 * 带 z 坐标、可以参与 [DepthSorter] 排序的对象。
 *
 * 排序器需要把 z 值映射成整数键来做计数排序，所以由实现方提供稳定的 [sortKey]。
 * 非负 z 值可以直接用 Float.toRawBits 得到与浮点大小一致的整数，无需额外映射表。
 */
interface DepthItem {
  /** 排序用的整数键：值越大越靠后绘制。 */
  val sortKey: Int

  /** 浮点 z 值，用于调试或自定义比较器。 */
  val z: Float
}
