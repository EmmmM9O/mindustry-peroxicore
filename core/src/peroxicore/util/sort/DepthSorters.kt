package peroxicore.util.sort

import arc.struct.*

/**
 * 排序器的统一入口与工厂。
 *
 * 这样调用方只需要依赖 [DepthSorter] 接口，切换实现时只改这一处：
 *
 * ```kotlin
 * val sorter = DepthSorters.counting<MyItem>()
 * ```
 *
 * 另外提供按规模自动挑选实现的 [bySize]。
 */
object DepthSorters {
  /** 计数排序（默认推荐）。 */
  fun <T : DepthItem> counting(initialCapacity: Int = 256): DepthSorter<T> =
    CountingDepthSorter(initialCapacity)

  /** 插入排序，适合几乎有序的序列。 */
  fun <T : DepthItem> insertion(): DepthSorter<T> = InsertionDepthSorter()

  /** 自定义比较器版本。 */
  fun <T : DepthItem> comparing(comparator: Comparator<in T>): DepthSorter<T> =
    DualPivotDepthSorter(comparator)

  /** 不排序。 */
  fun <T : DepthItem> none(): DepthSorter<T> = NullDepthSorter()

  /**
   * 按对象数量挑选实现。
   *
   * 小规模序列用插入排序就够（避免建段的固定开销），大规模用计数排序。
   *
   * @param count 预期元素数量
   * @param smallThreshold 不超过这个数量时使用插入排序
   */
  fun <T : DepthItem> bySize(count: Int, smallThreshold: Int = 32): DepthSorter<T> =
    if (count <= smallThreshold) InsertionDepthSorter() else CountingDepthSorter(count)

  /**
   * 可选：把所有实现串起来按优先级选择。
   *
   * 调用方可以传入若干候选，第一个 [DepthSorter] 就是最终使用的实现；
   * 这是一个扩展点，便于在运行时按平台切换（例如 Android 上用插入排序，桌面用计数排序）。
   */
  fun <T : DepthItem> firstOf(vararg candidates: DepthSorter<T>): DepthSorter<T> =
    candidates.firstOrNull() ?: NullDepthSorter()

  /**
   * 直接对一个 [Seq] 排序的便捷方法；内部使用共享的计数排序器。
   *
   * 注意：共享实例不可重入，不要在同一个排序过程中的回调里再次调用。
   */
  @Suppress("UNCHECKED_CAST")
  fun <T : DepthItem> sort(items: Seq<T>): Seq<T> {
    if (items.size <= 1) return items
    val sorter = shared as CountingDepthSorter<T>
    return sorter.sort(items)
  }

  private val shared = CountingDepthSorter<DepthItem>(1024)
}
