package peroxicore.util

/**
 * 可复用的 [MergedTile] 列表。
 *
 * 内部按槽位保存对象：清空或移除后的对象不会被丢弃，下一次 [add] 会直接复用， 因此稳定运行后合并过程几乎不再创建 [MergedTile] 实例。
 *
 * 该列表是“活”的：下一次合并会原地修改它。不要在跨帧缓存中持有它或其中的元素。
 */
class MergedTileBuffer internal constructor() : AbstractList<MergedTile>() {
  private var storage = arrayOfNulls<MergedTile>(16)
  private var count = 0

  internal var changeLog: MergeChangeLog? = null

  override val size: Int
    get() = count

  override fun get(index: Int): MergedTile {
    if (index < 0 || index >= count) {
      throw IndexOutOfBoundsException("index=$index, size=$count")
    }
    return storage[index]!!
  }

  /**
   * 追加一个矩形，返回它在缓冲区中的下标。
   *
   * 如果 [count] 位置已经有被移除/清空的对象，则复用它。 该方法仅供 [TileMerger.mergeInto] / [TileMerger.update] 在合并过程中调用。
   */
  fun add(data: Short, x: Int, y: Int, width: Short, height: Short): Int {
    if (count == storage.size) grow()
    val tile = TileData(data)
    val existing = storage[count]
    if (existing == null) {
      storage[count] = MergedTile(tile, x, y, width, height)
    } else {
      existing.set(tile, x, y, width, height)
    }
    val index = count
    count = index + 1
    changeLog?.addAdded(storage[index]!!)
    return index
  }

  /**
   * 移除 [index] 处的矩形，返回被移除的对象。
   *
   * 使用 swap-remove：最后一个矩形会被移动到 [index]；被移除的对象留在活动范围之外， 供下一次 [add] 复用。调用方需要同步更新被移动矩形在外部索引中的位置。
   */
  internal fun removeAt(index: Int): MergedTile {
    if (index < 0 || index >= count) {
      throw IndexOutOfBoundsException("index=$index, size=$count")
    }
    val removed = storage[index]!!
    changeLog?.addRemoved(removed)
    val last = count - 1
    if (index != last) {
      storage[index] = storage[last]
      storage[last] = removed
    }
    count = last
    return removed
  }

  /** 清空活动范围，但保留底层对象以便复用。 */
  internal fun clearBuffer() {
    if (count == 0) return
    count = 0
  }

  private fun grow() {
    val newSize = maxOf(16, storage.size * 2)
    storage = storage.copyOf(newSize)
  }
}
