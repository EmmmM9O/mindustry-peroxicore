package peroxicore.util

/**
 * 每个 [TileChunk] 复用的合并状态。
 *
 * - [output]：合并结果缓冲，复用 [MergedTile] 对象。
 * - [dirty]：自上次合并以来的脏矩形。
 * - owner / scratch：增量合并需要的额外结构，按需分配。
 */
class TileMergeState
internal constructor(
  val width: Short,
  val height: Short,
  val chunkSize: Int,
) {
  val output = MergedTileBuffer()

  val dirty = DirtyRegion()

  internal val changeLog = MergeChangeLog()

  internal var outputValid: Boolean = false

  internal var owner: IntArray? = null

  internal var ownerValid: Boolean = false

  internal var merger: TileMerger? = null

  internal var revision: Long = -1L

  internal var incrementalUpdates: Int = 0

  private var scanlineScratch: ScanlineScratch? = null

  private var greedyVisited: BooleanArray? = null

  internal fun ensureOwner(): IntArray = owner ?: IntArray(chunkSize) { -1 }.also { owner = it }

  internal fun ensureScanlineScratch(): ScanlineScratch =
    scanlineScratch ?: ScanlineScratch(width.toInt()).also { scanlineScratch = it }

  internal fun ensureGreedyVisited(): BooleanArray =
    greedyVisited ?: BooleanArray(chunkSize).also { greedyVisited = it }

  internal fun releaseIncrementalState() {
    owner = null
    ownerValid = false
  }

  override fun toString(): String =
    "TileMergeState(${width}x$height, rects=${output.size}, dirty=$dirty)"
}
