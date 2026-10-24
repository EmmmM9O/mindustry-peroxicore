package peroxicore.util

/**
 * 被移除的 [MergedTile] 的快照。
 *
 * 因为 [MergedTile] 会被复用，监听器不能直接持有被移除的对象； 快照本身也由 [MergeChangeLog] 复用，只应在回调期间使用。
 */
class MergedTileSnapshot internal constructor() {
  var data: Short = 0
    internal set

  var x: Int = 0
    internal set

  var y: Int = 0
    internal set

  var width: Short = 0
    internal set

  var height: Short = 0
    internal set

  val right: Int
    get() = x + width.toInt()

  val bottom: Int
    get() = y + height.toInt()

  val area: Int
    get() = width.toInt() * height.toInt()

  internal fun set(tile: MergedTile) {
    data = tile.data
    x = tile.x
    y = tile.y
    width = tile.width
    height = tile.height
  }

  override fun toString(): String = "MergedTileSnapshot(data=$data, x=$x, y=$y, ${width}x$height)"
}
