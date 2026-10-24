package peroxicore.util

/**
 * 一次合并得到的矩形 tile。
 *
 * [x] / [y] 是代表 tile 的坐标，同时也是矩形左上角；[tile] 是代表 tile 的数据。 坐标单位是 tile，不是像素。
 *
 * 注意：为了减少 GC，[MergedTileBuffer] 会复用这些对象。不要长期持有单个 [MergedTile] 引用，渲染时每次从 [TileChunk.merged]
 * 返回的列表中读取。
 */
class MergedTile
internal constructor(
  tile: TileData,
  x: Int,
  y: Int,
  width: Short,
  height: Short,
) {
  var tile: TileData = tile
    internal set

  var x: Int = x
    internal set

  var y: Int = y
    internal set

  var width: Short = width
    internal set

  var height: Short = height
    internal set

  /** 代表 tile 的原始数据，等价于 `tile.data`。 */
  val data: Short
    get() = tile.data

  /** 右边界（不包含）。 */
  val right: Int
    get() = x + width.toInt()

  /** 下边界（不包含）。 */
  val bottom: Int
    get() = y + height.toInt()

  /** 覆盖的 tile 数量。 */
  val area: Int
    get() = width.toInt() * height.toInt()

  internal fun set(tile: TileData, x: Int, y: Int, width: Short, height: Short) {
    this.tile = tile
    this.x = x
    this.y = y
    this.width = width
    this.height = height
  }

  override fun toString(): String = "MergedTile(data=${tile.data}, x=$x, y=$y, ${width}x$height)"
}
