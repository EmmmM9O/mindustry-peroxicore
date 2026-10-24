package peroxicore.util

/** 合并质量与速度的权衡。 */
enum class MergeQuality {
  /** 单方向贪心，速度最快。 */
  FAST,

  /** 两个方向贪心，取矩形数较少者；默认。 */
  BALANCED,

  /** 四个方向贪心，取矩形数最少者；质量最好，但仍不是全局最优，耗时约为单方向的 4 倍。 */
  QUALITY,
}

/**
 * 贪心矩形合并器。
 *
 * 从某个方向扫描，遇到未覆盖的 tile 时先向右扩展宽度，再向下扩展高度， 然后把整个矩形标记为已覆盖。多方向模式会从不同角扫描并选择矩形数最少的方案。
 *
 * 结果写入 [TileMergeState.output]；扫描用的 visited 数组由状态复用。多方向模式先做 纯计数扫描，确定最佳方向后再真正写出结果，避免创建临时矩形列表。
 */
class GreedyTileMerger(val quality: MergeQuality = MergeQuality.BALANCED) : TileMerger {
  override fun mergeInto(chunk: TileChunk, state: TileMergeState) {
    val visited = state.ensureGreedyVisited()
    visited.fill(false)

    when (quality) {
      MergeQuality.FAST -> mergePass(chunk, visited, false, false, state.output)
      MergeQuality.BALANCED -> {
        val pass = bestPass(chunk, visited, 2)
        visited.fill(false)
        mergePass(chunk, visited, flipX(pass), flipY(pass), state.output)
      }
      MergeQuality.QUALITY -> {
        val pass = bestPass(chunk, visited, 4)
        visited.fill(false)
        mergePass(chunk, visited, flipX(pass), flipY(pass), state.output)
      }
    }
  }

  /** 只计数，不创建矩形，选出矩形数最少的扫描方向。 */
  private fun bestPass(chunk: TileChunk, visited: BooleanArray, passes: Int): Int {
    var best = 0
    var bestCount = Int.MAX_VALUE
    for (pass in 0 until passes) {
      if (pass != 0) visited.fill(false)
      val count = mergePass(chunk, visited, flipX(pass), flipY(pass), null)
      if (count < bestCount) {
        bestCount = count
        best = pass
      }
    }
    return best
  }

  private fun flipX(pass: Int): Boolean = pass == 1 || pass == 2

  private fun flipY(pass: Int): Boolean = pass == 1 || pass == 3

  /**
   * 单次贪心扫描。
   *
   * [out] 为 null 时只返回矩形数，用于多方向比较；否则写入 [out]。
   */
  private fun mergePass(
    chunk: TileChunk,
    visited: BooleanArray,
    flipX: Boolean,
    flipY: Boolean,
    out: MergedTileBuffer?,
  ): Int {
    val width = chunk.widthInt
    val height = chunk.heightInt
    val data = chunk.data
    val xStep = if (flipX) -1 else 1
    val yStep = if (flipY) -1 else 1
    var count = 0

    for (logicalY in 0 until height) {
      val physicalY = if (flipY) height - 1 - logicalY else logicalY
      val rowStart = physicalY * width
      val logicalRowStart = logicalY * width
      var logicalX = 0
      while (logicalX < width) {
        val logicalIndex = logicalRowStart + logicalX
        if (visited[logicalIndex]) {
          logicalX++
          continue
        }

        val physicalX = if (flipX) width - 1 - logicalX else logicalX
        val value = data[rowStart + physicalX]

        // 先尽量向右扩展宽度。
        var runWidth = 1
        var nextPhysicalX = physicalX + xStep
        while (logicalX + runWidth < width) {
          if (visited[logicalIndex + runWidth] || data[rowStart + nextPhysicalX] != value) break
          runWidth++
          nextPhysicalX += xStep
        }

        // 再尽量向下扩展高度，要求整行宽度都相同且未被覆盖。
        var runHeight = 1
        var nextPhysicalY = physicalY + yStep
        var nextLogicalRow = logicalRowStart + width + logicalX
        heightLoop@ while (logicalY + runHeight < height) {
          val nextRowStart = nextPhysicalY * width
          var offset = 0
          var physicalXInRow = physicalX
          while (offset < runWidth) {
            if (visited[nextLogicalRow + offset] || data[nextRowStart + physicalXInRow] != value) {
              break@heightLoop
            }
            offset++
            physicalXInRow += xStep
          }
          runHeight++
          nextLogicalRow += width
          nextPhysicalY += yStep
        }

        // 每个 cell 只会在这里被标记一次，因此整个扫描是 O(width * height)。
        var markStart = logicalRowStart + logicalX
        for (y in 0 until runHeight) {
          for (x in 0 until runWidth) visited[markStart + x] = true
          markStart += width
        }

        val mergedX = if (flipX) width - logicalX - runWidth else logicalX
        val mergedY = if (flipY) height - logicalY - runHeight else logicalY
        if (out != null) out.add(value, mergedX, mergedY, runWidth.toShort(), runHeight.toShort())
        count++
        logicalX += runWidth
      }
    }
    return count
  }
}
