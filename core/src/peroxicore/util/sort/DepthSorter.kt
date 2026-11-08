package peroxicore.util.sort

import arc.struct.*

/**
 * 按 z 值给一组对象排序的通用接口。
 *
 * 设计上把"排序算法"和"存放对象的数据结构"分开：
 *
 * - [DepthSorter] 只定义次序，不关心对象从哪来、排完去哪；
 * - 具体算法可以随时替换（计数排序、插入排序、稀疏索引……），调用方代码不用改；
 * - 实现内部复用缓冲区，排序过程不分配。
 *
 * 用法：
 *
 * ```kotlin
 * val sorter = CountingDepthSorter<MyItem>()
 * sorter.sort(items)                       // 原地排序
 * sorter.forEachDraw(items) { draw(it) }   // 或按顺序绘制而不改动 items
 * ```
 *
 * @param T 参与排序的对象类型
 */
interface DepthSorter<T : DepthItem> {
  /**
   * 就地排序 [items]，按 [DepthItem.sortKey] 升序。
   *
   * 结果必须稳定：键相同的对象保持原有相对顺序。
   *
   * @return [items] 本身，便于链式调用
   */
  fun <S : Seq<T>> sort(items: S): S

  /**
   * 按排好的顺序遍历 [items]，不修改 [items] 本身的顺序。
   *
   * 默认实现调用 [sort]；子类重写时可以做到"只排索引、不动对象"。
   */
  fun forEachDraw(items: Seq<T>, consumer: (T) -> Unit) {
    sort(items)
    for (i in 0 until items.size) consumer(items.get(i))
  }

  /** 内部缓冲区当前占用的元素数，用于观察是否真的在复用。 */
  val bufferSize: Int get() = 0
}
