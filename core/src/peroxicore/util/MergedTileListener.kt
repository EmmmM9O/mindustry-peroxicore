package peroxicore.util

/**
 * 监听某个 [TileChunk] 的合并结果变化。
 *
 * - 增量更新时依次回调 [onTileRemoved] / [onTileAdded]，最后回调 [onIncrementalMerge]；
 * - 全量重合并时只回调 [onFullMerge]；
 * - 尺寸/位置/数据变化在增量路径中表现为“移除旧矩形 + 新增新矩形”。
 *
 * 回调中的 [MergedTile] / [MergedTileSnapshot] 由内部复用，只应在回调期间使用。
 */
interface MergedTileListener {
  /** 新增一个合并矩形。 */
  fun onTileAdded(chunk: TileChunk, tile: MergedTile) {}

  /** 移除一个合并矩形；[tile] 是移除前的快照。 */
  fun onTileRemoved(chunk: TileChunk, tile: MergedTileSnapshot) {}

  /** 一次增量更新完成；[merged] 是完整的最新结果。 */
  fun onIncrementalMerge(chunk: TileChunk, merged: List<MergedTile>) {}

  /** 发生了全量重合并；[merged] 是完整的新结果。 */
  fun onFullMerge(chunk: TileChunk, merged: List<MergedTile>)
}
