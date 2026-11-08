package peroxicore.util.sort

import arc.struct.*

/**
 * 通用比较排序版本，支持自定义 [comparator]，用于计数排序不适用的场景。
 *
 * 什么时候需要它：
 *
 * - [DepthItem.sortKey] 的取值跨度极大（计数排序的桶会很稀疏）；
 * - 需要按 z 之外的条件排序（例如 z 相同时按材质 id 排）。
 *
 * 内部排序直接复用 [Seq.sort]（Arc 的 TimSort，稳定），临时缓冲由 Arc 自己的单例持有，
 * 因此反复调用时同样不会分配。
 *
 * @param comparator 自定义比较器；默认按 [DepthItem.sortKey] 升序
 */
class DualPivotDepthSorter<T : DepthItem>(
  private var comparator: Comparator<in T> = Comparator { a, b -> a.sortKey.compareTo(b.sortKey) },
) : DepthSorter<T> {

  /** 更换比较器。 */
  fun setComparator(comparator: Comparator<in T>): DualPivotDepthSorter<T> = apply {
    this.comparator = comparator
  }

  @Suppress("UNCHECKED_CAST")
  override fun <S : Seq<T>> sort(items: S): S {
    if (items.size <= 1) return items
    items.sort(comparator as Comparator<in T>)
    return items
  }
}
