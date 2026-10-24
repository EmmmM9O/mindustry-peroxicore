package peroxicore.util

/**
 * 基于扫描线和横向 run 的 [TileMerger] 实现。
 *
 * 每一行先切成横向 run，再把 x、width、data 都与上一行完全相同的 run 垂直延伸； 无法延伸的 run 立即输出为矩形。
 *
 * 与 [GreedyTileMerger] 相比：
 * - 不需要 visited 数组；
 * - 不做逐 cell 的向下试探和标记；
 * - 匹配阶段只遍历 run，不遍历 cell。
 *
 * 对于横向连续区域较多的地图（大片同色、条纹、块状地形），通常比 [GreedyTileMerger] 快数倍；在高熵噪声图上优势较小，矩形数可能略多。
 *
 * 扫描数组由 [TileMergeState] 复用，不会在每次合并时重新分配。
 */
class ScanlineTileMerger : TileMerger {
  override fun mergeInto(chunk: TileChunk, state: TileMergeState) {
    scanlineMergeRegion(
      chunk = chunk,
      left = 0,
      top = 0,
      right = chunk.widthInt,
      bottom = chunk.heightInt,
      out = state.output,
      owner = null,
      scratch = state.ensureScanlineScratch(),
    )
  }
}
