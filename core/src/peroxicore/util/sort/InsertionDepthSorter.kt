package peroxicore.util.sort

import arc.struct.*

/**
 * 插入排序版本的 z 排序器。
 *
 * 适合**几乎已有序**的序列（例如每帧只移动少量对象）：这种情况下插入排序接近 O(n)，
 * 而且实现最简单、没有额外缓冲。对象几乎随机分布时请改用 [CountingDepthSorter]。
 *
 * 稳定排序，不分配。
 */
class InsertionDepthSorter<T : DepthItem> : DepthSorter<T> {
  override fun <S : Seq<T>> sort(items: S): S {
    val n = items.size
    if (n <= 1) return items

    for (i in 1 until n) {
      val current = items.get(i)
      val key = current.sortKey
      var j = i - 1
      // 只有严格大于才后移，保证相等键的原有顺序（稳定）
      while (j >= 0 && items.get(j).sortKey > key) {
        items.set(j + 1, items.get(j))
        j--
      }
      items.set(j + 1, current)
    }
    return items
  }
}
