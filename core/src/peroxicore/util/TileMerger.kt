package peroxicore.util

/**
 * tile 合并器抽象。
 *
 * 实现负责把 [TileChunk] 中数据相同的相邻 tile 合并成若干矩形。
 *
 * 热路径使用 [mergeInto]，结果写入 [TileMergeState.output]，从而复用 [MergedTile] 对象。 [update] 用于增量更新：根据
 * [TileMergeState.dirty] 只调整受影响区域；返回 false 表示 当前状态无法安全增量更新，调用方应回退到全量合并。
 *
 * 内置实现：
 * - [GreedyTileMerger]：质量/速度可调；
 * - [ScanlineTileMerger]：扫描线 run 合并，速度优先；
 * - [IncrementalTileMerger]：在已有结果上做局部增量更新。
 */
fun interface TileMerger {
  /** 全量合并到 [state] 的 output；调用方负责先清空 output。 */
  fun mergeInto(chunk: TileChunk, state: TileMergeState)

  /** 尝试增量更新；返回 true 表示 [state] 已经是最新结果。 */
  fun update(chunk: TileChunk, state: TileMergeState): Boolean = false

  /** 是否支持 [update]。 */
  fun supportsIncrementalUpdate(): Boolean = false

  /**
   * 便利方法：使用临时状态返回一份结果。
   *
   * 频繁调用时请使用 [TileChunk.merged]，它会缓存结果并复用列表与 [MergedTile] 对象。
   */
  fun merge(chunk: TileChunk): List<MergedTile> {
    val state = TileMergeState(chunk.width, chunk.height, chunk.size)
    mergeInto(chunk, state)
    return state.output
  }
}
