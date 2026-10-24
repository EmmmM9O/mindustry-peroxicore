package peroxicore.util

/**
 * 一个 width x height 的 tile 区块。
 *
 * width/height 使用 Short 存储；总 tile 数不能超过 [MAX_CHUNK_SIZE]（1024 * 1024）。 数据用 [ShortArray]
 * 扁平存储；[TileData] 是 value class，避免逐 tile 对象。 修改只更新 [revision] 并记录脏矩形，[merged] 时才真正合并。
 *
 * [merged] 返回的列表由区块复用：同一区块、同一合并器的多次调用会返回同一个列表， 下一次合并会原地更新它，[MergedTile] 对象也会被复用。渲染时每帧重新读取列表即可，
 * 不要长期持有列表或其中的元素引用。
 *
 * 可以通过 [mergedTileListener] 监听合并结果的新增/移除；全量重合并会走单独的 [MergedTileListener.onFullMerge] 回调。
 *
 * 该类不是线程安全的。
 */
class TileChunk(
  val width: Short,
  val height: Short,
) {
  constructor(
    width: Int,
    height: Int,
  ) : this(
    checkedDimension(width).toShort(),
    checkedDimension(height).toShort(),
  )

  internal val widthInt: Int = width.toInt()
  internal val heightInt: Int = height.toInt()
  val size: Int = widthInt * heightInt

  init {
    require(widthInt > 0) { "width must be > 0: $width" }
    require(heightInt > 0) { "height must be > 0: $height" }
    require(size.toLong() <= MAX_CHUNK_SIZE.toLong()) {
      "chunk is too large: ${width}x$height = $size (max $MAX_CHUNK_SIZE)"
    }
  }

  /** 扁平 tile 数据；仅供同包合并器读取，禁止修改。 */
  internal val data = ShortArray(size)

  private var mergeState: TileMergeState? = null

  /** 数据版本号；每次实际改变数据时递增。 */
  var revision: Long = 0L
    private set

  /**
   * 合并结果监听器。
   *
   * 设置一个非 null 监听器时，如果当前已经有一份有效结果，会立即用 [MergedTileListener.onFullMerge] 发送一次完整快照作为基线；
   * 否则在下一次合并完成时发送。
   */
  var mergedTileListener: MergedTileListener? = null
    set(value) {
      if (field === value) return
      field = value
      if (value == null) return
      val state = mergeState
      if (state != null && state.outputValid) {
        value.onFullMerge(this, state.output)
      }
    }

  /** 读取 ([x], [y]) 处的 tile。 */
  operator fun get(x: Int, y: Int): TileData = TileData(data[index(x, y)])

  /** 读取 ([x], [y]) 处的原始数据，避免创建 [TileData]。 */
  fun dataAt(x: Int, y: Int): Short = data[index(x, y)]

  /** 写入 ([x], [y]) 处的 tile。 */
  operator fun set(x: Int, y: Int, tile: TileData) {
    setData(x, y, tile.data)
  }

  /** 写入 ([x], [y]) 处的数据；值未变化时不会使 [revision] 递增。 */
  fun setData(x: Int, y: Int, value: Short) {
    val i = index(x, y)
    if (data[i] == value) return
    data[i] = value
    revision++
    val state = mergeState
    if (state?.merger?.supportsIncrementalUpdate() == true) {
      state.dirty.include(x, y)
    }
  }

  /** 用 [tile] 填充整个区块。 */
  fun fill(tile: TileData) {
    fill(tile.data)
  }

  /** 用 [value] 填充整个区块。 */
  fun fill(value: Short) {
    data.fill(value)
    revision++
    val state = mergeState
    if (state?.merger?.supportsIncrementalUpdate() == true) {
      state.dirty.include(0, 0, widthInt, heightInt)
    }
  }

  /**
   * 返回覆盖整个区块的合并结果。
   *
   * - 同一 merger + 同一 revision 直接返回缓存；
   * - 支持增量的 merger 会在 [TileMergeState.dirty] 上做局部更新；
   * - 否则做全量合并。
   *
   * 返回的列表和 [MergedTile] 对象会被原地复用。
   */
  fun merged(merger: TileMerger): List<MergedTile> {
    val state = mergeState ?: TileMergeState(width, height, size).also { mergeState = it }
    val listener = mergedTileListener

    if (
      state.merger === merger &&
        state.revision == revision &&
        state.dirty.isEmpty &&
        state.outputValid
    ) {
      return state.output
    }

    if (
      state.merger === merger &&
        state.outputValid &&
        state.ownerValid &&
        !state.dirty.isEmpty &&
        merger.supportsIncrementalUpdate()
    ) {
      state.changeLog.clear()
      if (listener != null) {
        state.output.changeLog = state.changeLog
      }
      state.outputValid = false
      val updated =
        try {
          merger.update(this, state)
        } finally {
          state.output.changeLog = null
        }

      if (updated) {
        state.revision = revision
        state.dirty.clear()
        state.outputValid = true
        if (listener != null) {
          try {
            notifyIncremental(listener, state)
          } finally {
            state.changeLog.clear()
          }
        }
        return state.output
      }
      state.changeLog.clear()
    }

    state.changeLog.clear()
    state.output.changeLog = null
    state.outputValid = false
    state.output.clearBuffer()
    state.ownerValid = false
    if (!merger.supportsIncrementalUpdate()) {
      state.releaseIncrementalState()
    }
    merger.mergeInto(this, state)
    state.merger = merger
    state.revision = revision
    state.dirty.clear()
    state.outputValid = true
    listener?.onFullMerge(this, state.output)
    return state.output
  }

  /**
   * 丢弃合并缓存，但保持 [revision] 不变。
   *
   * 适用于自定义合并器的内部状态发生变化，或外部直接修改了数据的情况。
   */
  fun invalidateMerged() {
    mergeState?.let {
      it.merger = null
      it.revision = -1L
      it.outputValid = false
    }
  }

  /**
   * 把 tile 坐标转换为 [data] 的下标。
   *
   * @throws IndexOutOfBoundsException 坐标越界
   */
  fun index(x: Int, y: Int): Int {
    if (x < 0 || x >= widthInt || y < 0 || y >= heightInt) {
      throw IndexOutOfBoundsException("($x, $y) out of bounds for ${width}x$height")
    }
    return y * widthInt + x
  }

  private fun notifyIncremental(listener: MergedTileListener, state: TileMergeState) {
    val log = state.changeLog
    for (i in 0 until log.removed.size) {
      listener.onTileRemoved(this, log.removed[i])
    }
    for (i in 0 until log.added.size) {
      listener.onTileAdded(this, log.added[i])
    }
    listener.onIncrementalMerge(this, state.output)
  }

  companion object {
    /** 单个 chunk 允许的最大 tile 数。 */
    const val MAX_CHUNK_SIZE: Int = 1024 * 1024
  }
}

private fun checkedDimension(value: Int): Int {
  require(value in 1..Short.MAX_VALUE.toInt()) {
    "dimension out of range: $value"
  }
  return value
}
