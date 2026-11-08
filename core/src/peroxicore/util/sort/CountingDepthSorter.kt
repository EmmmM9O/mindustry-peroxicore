package peroxicore.util.sort

import arc.struct.*

/**
 * 基于计数排序的 z 排序器，算法参考 arc.graphics.g2d.SpriteBatch 的 request 排序。
 *
 * 思路：
 *
 * 1. 把 z 值与**紧邻的前一个对象**相同的对象合并成一个"连续段"，记录每段的 (键, 起始, 长度)。
 *    绘制调用通常按深度成批发出，所以段数往往远小于对象数；
 * 2. 按段键对段做计数排序，得到段的先后次序；
 * 3. 按段次序把对象整体复制到临时数组，再复制回去。
 *
 * 这样同一深度的成批对象只作为一个单元参与排序，搬运靠 System.arraycopy 完成，既快又**稳定**。
 *
 * 所有临时数组都保存在实例里，重复排序不会分配。
 *
 * @param T 参与排序的对象类型
 * @param initialCapacity 临时数组的初始容量，仅影响首次扩容次数
 */
class CountingDepthSorter<T : DepthItem>(initialCapacity: Int = 256) : DepthSorter<T> {
  /** 段表：每段 3 个 int，依次是 键、起始下标、长度。 */
  private var segments = IntArray(maxOf(initialCapacity, 16) * 3)

  /** 按段重排时的对象缓冲。 */
  private var copy = arrayOfNulls<Any?>(maxOf(initialCapacity, 16))

  /** 段的数量。 */
  private var segmentCount = 0

  override val bufferSize: Int get() = segmentCount

  override fun <S : Seq<T>> sort(items: S): S {
    val n = items.size
    if (n <= 1) return items

    buildSegments(items, n)
    if (segmentCount <= 1) return items

    sortSegmentKeys()

    if (copy.size < n) copy = arrayOfNulls(maxOf(n, copy.size * 2))
    val buffer = copy
    var out = 0
    for (i in 0 until segmentCount) {
      val start = segments[i * 3 + 1]
      val length = segments[i * 3 + 2]
      // 直接用 Seq 的公开 API 拷贝，不要碰 items：Kotlin 会对 T[] 插入类型检查
      for (j in 0 until length) buffer[out + j] = items.get(start + j)
      out += length
    }
    for (i in 0 until n) {
      @Suppress("UNCHECKED_CAST")
      items.set(i, buffer[i] as T)
    }
    return items
  }

  override fun forEachDraw(items: Seq<T>, consumer: (T) -> Unit) {
    val n = items.size
    if (n == 0) return

    buildSegments(items, n)
    if (segmentCount <= 1) {
      for (i in 0 until n) consumer(items.get(i))
      return
    }

    sortSegmentKeys()
    for (i in 0 until segmentCount) {
      val start = segments[i * 3 + 1]
      val end = start + segments[i * 3 + 2]
      for (j in start until end) consumer(items.get(j))
    }
  }

  /** 扫描一遍，按"与前一对象同键"合并成连续段。 */
  private fun buildSegments(items: Seq<T>, n: Int) {
    if (segments.size < n * 3) segments = IntArray(n * 3)
    val seg = segments

    var ci = 0
    var key = items.get(0).sortKey
    var start = 0
    for (i in 1 until n) {
      val current = items.get(i).sortKey
      if (current != key) {
        seg[ci] = key
        seg[ci + 1] = start
        seg[ci + 2] = i - start
        ci += 3
        key = current
        start = i
      }
    }
    seg[ci] = key
    seg[ci + 1] = start
    seg[ci + 2] = n - start
    segmentCount = ci / 3 + 1
  }

  /**
   * 按段键对段排序。
   *
   * - 段很少时用插入排序（几十个段时通常比建映射表更快）；
   * - 段很多时改用计数排序，避免插入排序的平方复杂度。
   */
  private fun sortSegmentKeys() {
    val count = segmentCount
    if (count <= 1) return

    if (count <= INSERTION_THRESHOLD) {
      insertionSortSegments(count)
    } else {
      countingSortSegments(count)
    }
  }

  private fun insertionSortSegments(count: Int) {
    val seg = segments
    for (i in 1 until count) {
      val key = seg[i * 3]
      val start = seg[i * 3 + 1]
      val length = seg[i * 3 + 2]
      var j = i - 1
      while (j >= 0 && seg[j * 3] > key) {
        seg[(j + 1) * 3] = seg[j * 3]
        seg[(j + 1) * 3 + 1] = seg[j * 3 + 1]
        seg[(j + 1) * 3 + 2] = seg[j * 3 + 2]
        j--
      }
      seg[(j + 1) * 3] = key
      seg[(j + 1) * 3 + 1] = start
      seg[(j + 1) * 3 + 2] = length
    }
  }

  /** 计数排序：键 -> 桶，再按下标顺序写回。稳定，因为桶内按出现顺序。 */
  private fun countingSortSegments(count: Int) {
    // 收集所有不同的键
    val uniqueKeys = keysScratch
    uniqueKeys.size = 0
    for (i in 0 until count) {
      uniqueKeys.add(segments[i * 3])
    }
    uniqueKeys.sort()

    // 去重
    var unique = 0
    for (i in 0 until uniqueKeys.size) {
      if (unique == 0 || uniqueKeys.get(unique - 1) != uniqueKeys.get(i)) {
        uniqueKeys.set(unique++, uniqueKeys.get(i))
      }
    }
    uniqueKeys.size = unique

    // 键 -> 桶下标
    keyToBucket.clear()
    for (i in 0 until unique) keyToBucket.put(uniqueKeys.get(i), i)

    // 统计每桶数量
    if (bucketCounts.size < unique) bucketCounts = IntArray(unique * 2)
    for (i in 0 until unique) bucketCounts[i] = 0
    for (i in 0 until count) bucketCounts[keyToBucket.get(segments[i * 3])]++

    // 前缀和得到每个桶的起始位置
    var offset = 0
    for (i in 0 until unique) {
      val c = bucketCounts[i]
      bucketCounts[i] = offset
      offset += c
    }

    // 稳定地按顺序放入临时数组
    if (sortedSegments.size < count * 3) sortedSegments = IntArray(count * 6)
    val dest = sortedSegments
    for (i in 0 until count) {
      val bucket = keyToBucket.get(segments[i * 3])
      val at = bucketCounts[bucket] * 3
      dest[at] = segments[i * 3]
      dest[at + 1] = segments[i * 3 + 1]
      dest[at + 2] = segments[i * 3 + 2]
      bucketCounts[bucket]++
    }

    // 写回
    System.arraycopy(dest, 0, segments, 0, count * 3)
  }

  private val keysScratch = IntSeq()
  private val keyToBucket = IntIntMap()
  private var bucketCounts = IntArray(64)
  private var sortedSegments = IntArray(256)

  companion object {
    /** 段数不超过这个值时用插入排序。 */
    const val INSERTION_THRESHOLD: Int = 48
  }
}
