package peroxicore.util

import java.util.*

/**
 * 在已有合并结果上做局部增量更新的 [TileMerger]。
 *
 * 全量合并委托给 [delegate]（默认 [ScanlineTileMerger]），同时建立 cell -> 矩形索引。 之后 [TileChunk] 的批量修改只记录脏包围盒；下一次
 * [update]：
 *
 * 1. 从脏区域（加 [dirtyMargin] 边距）开始；
 * 2. 找出与区域相交的旧矩形，把它们从结果中移除，并把区域扩展到这些矩形的完整包围盒；
 * 3. 重复直到区域内的 cell 全部无主；
 * 4. 只在这个区域内重新做扫描线合并，并更新 cell -> 矩形索引。
 *
 * 未受影响的矩形对象保持不变。面积过大、修改次数过多或结构过于复杂时返回 false， 由 [TileChunk] 回退到全量合并，避免增量路径退化成大规模扫描。
 *
 * 计数器 [incrementalUpdates] / [fullMerges] 仅用于诊断；合并器本身不保存 每-chunk 的布局状态，可以跨 chunk 复用（但计数器不是线程安全的）。
 *
 * @param delegate 全量合并器，不能本身支持增量更新
 * @param dirtyMargin 脏区域向外扩展的 tile 数，用于改善局部合并边界
 * @param maxIncrementalAreaRatio 增量区域面积上限占 chunk 面积的比例
 * @param fullRebuildInterval 每多少次增量更新后强制全量重建一次，0 表示不强制
 * @param maxRemovedRectangles 单次增量最多移除的矩形数，超过则回退全量合并
 */
class IncrementalTileMerger(
  private val delegate: TileMerger = ScanlineTileMerger(),
  private val dirtyMargin: Int = 1,
  private val maxIncrementalAreaRatio: Float = 0.25f,
  private val fullRebuildInterval: Int = 128,
  private val maxRemovedRectangles: Int = 4096,
) : TileMerger {
  init {
    require(!delegate.supportsIncrementalUpdate()) {
      "delegate must be a full merger"
    }
    require(dirtyMargin >= 0) { "dirtyMargin must be >= 0" }
    require(maxIncrementalAreaRatio > 0f) {
      "maxIncrementalAreaRatio must be > 0"
    }
    require(fullRebuildInterval >= 0) {
      "fullRebuildInterval must be >= 0"
    }
    require(maxRemovedRectangles > 0) {
      "maxRemovedRectangles must be > 0"
    }
  }

  /** 成功执行的增量更新次数（诊断用）。 */
  var incrementalUpdates: Int = 0
    private set

  /** 全量合并次数（诊断用）。 */
  var fullMerges: Int = 0
    private set

  override fun supportsIncrementalUpdate(): Boolean = true

  override fun mergeInto(chunk: TileChunk, state: TileMergeState) {
    fullMerges++
    delegate.mergeInto(chunk, state)
    rebuildOwner(chunk, state)
    state.incrementalUpdates = 0
  }

  override fun update(chunk: TileChunk, state: TileMergeState): Boolean {
    if (!state.ownerValid) return false
    if (fullRebuildInterval > 0 && state.incrementalUpdates >= fullRebuildInterval) {
      return false
    }

    val dirty = state.dirty
    if (dirty.isEmpty) return true

    val width = chunk.widthInt
    val height = chunk.heightInt
    val maxArea = maxOf(64, (chunk.size * maxIncrementalAreaRatio).toInt())

    var left = (dirty.minX - dirtyMargin).coerceAtLeast(0)
    var top = (dirty.minY - dirtyMargin).coerceAtLeast(0)
    var right = (dirty.maxX + 1 + dirtyMargin).coerceAtMost(width)
    var bottom = (dirty.maxY + 1 + dirtyMargin).coerceAtMost(height)
    if (area(left, top, right, bottom) > maxArea) return false

    val owner = state.owner ?: return false
    val out = state.output
    var removed = 0

    // 把区域扩展到所有相交旧矩形的完整包围盒，直到区域内不再有 owner。
    while (true) {
      var expanded = false
      var y = top
      while (y < bottom) {
        val rowStart = y * width
        var x = left
        while (x < right) {
          val rectIndex = owner[rowStart + x]
          if (rectIndex >= 0) {
            val rect = out[rectIndex]
            if (rect.x < left) {
              left = rect.x
              expanded = true
            }
            if (rect.y < top) {
              top = rect.y
              expanded = true
            }
            if (rect.right > right) {
              right = rect.right
              expanded = true
            }
            if (rect.bottom > bottom) {
              bottom = rect.bottom
              expanded = true
            }
            if (area(left, top, right, bottom) > maxArea) return false
            removeRect(chunk, state, owner, rectIndex)
            removed++
            if (removed > maxRemovedRectangles) return false
          }
          x++
        }
        y++
      }
      if (!expanded) break
    }

    // 区域内所有 cell 都已无主，可以安全局部重合并。
    scanlineMergeRegion(
      chunk = chunk,
      left = left,
      top = top,
      right = right,
      bottom = bottom,
      out = out,
      owner = owner,
      scratch = state.ensureScanlineScratch(),
    )

    state.incrementalUpdates++
    incrementalUpdates++
    return true
  }

  private fun rebuildOwner(chunk: TileChunk, state: TileMergeState) {
    val owner = state.ensureOwner()
    Arrays.fill(owner, -1)
    val out = state.output
    val width = chunk.widthInt
    for (i in 0 until out.size) {
      val rect = out[i]
      fillOwner(owner, width, rect.x, rect.y, rect.width.toInt(), rect.height.toInt(), i)
    }
    state.ownerValid = true
  }

  private fun removeRect(
    chunk: TileChunk,
    state: TileMergeState,
    owner: IntArray,
    index: Int,
  ) {
    val out = state.output
    val width = chunk.widthInt
    val rect = out[index]
    clearOwner(owner, width, rect.x, rect.y, rect.width.toInt(), rect.height.toInt())
    out.removeAt(index)

    // swap-remove 后，最后一个矩形被移到了 index。
    if (index < out.size) {
      val moved = out[index]
      fillOwner(owner, width, moved.x, moved.y, moved.width.toInt(), moved.height.toInt(), index)
    }
  }

  private fun area(left: Int, top: Int, right: Int, bottom: Int): Int =
    (right - left) * (bottom - top)
}
