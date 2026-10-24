package peroxicore.util

import java.util.*

/**
 * 在 [left, right) x [top, bottom) 区域上执行扫描线 run 合并。
 *
 * [owner] 非 null 时，额外把新矩形写回 cell -> rect 索引网格。
 */
internal fun scanlineMergeRegion(
  chunk: TileChunk,
  left: Int,
  top: Int,
  right: Int,
  bottom: Int,
  out: MergedTileBuffer,
  owner: IntArray?,
  scratch: ScanlineScratch,
) {
  val width = chunk.widthInt
  val data = chunk.data
  var prevX = scratch.prevX
  var prevWidth = scratch.prevWidth
  var prevData = scratch.prevData
  var prevStartY = scratch.prevStartY
  var curX = scratch.curX
  var curWidth = scratch.curWidth
  var curData = scratch.curData
  var curStartY = scratch.curStartY
  var prevCount = 0
  var curCount = 0

  for (y in top until bottom) {
    curCount = 0
    val rowStart = y * width
    var x = left
    while (x < right) {
      val value = data[rowStart + x]
      var end = x + 1
      while (end < right && data[rowStart + end] == value) end++
      curX[curCount] = x
      curWidth[curCount] = end - x
      curData[curCount] = value
      curCount++
      x = end
    }

    var p = 0
    var c = 0
    while (p < prevCount && c < curCount) {
      val px = prevX[p]
      val cx = curX[c]
      when {
        px < cx -> {
          emit(out, owner, width, prevX, prevWidth, prevData, prevStartY, p, y)
          p++
        }
        cx < px -> {
          curStartY[c] = y
          c++
        }
        else -> {
          if (prevWidth[p] == curWidth[c] && prevData[p] == curData[c]) {
            curStartY[c] = prevStartY[p]
          } else {
            emit(out, owner, width, prevX, prevWidth, prevData, prevStartY, p, y)
            curStartY[c] = y
          }
          p++
          c++
        }
      }
    }
    while (p < prevCount) {
      emit(out, owner, width, prevX, prevWidth, prevData, prevStartY, p, y)
      p++
    }
    while (c < curCount) {
      curStartY[c] = y
      c++
    }

    var swapInt = prevX
    prevX = curX
    curX = swapInt
    swapInt = prevWidth
    prevWidth = curWidth
    curWidth = swapInt
    var swapShort = prevData
    prevData = curData
    curData = swapShort
    swapInt = prevStartY
    prevStartY = curStartY
    curStartY = swapInt
    prevCount = curCount
  }

  for (i in 0 until prevCount) {
    emit(out, owner, width, prevX, prevWidth, prevData, prevStartY, i, bottom)
  }
}

private fun emit(
  out: MergedTileBuffer,
  owner: IntArray?,
  chunkWidth: Int,
  xs: IntArray,
  widths: IntArray,
  values: ShortArray,
  startYs: IntArray,
  index: Int,
  endY: Int,
) {
  val x = xs[index]
  val y = startYs[index]
  val width = widths[index]
  val height = endY - y
  val outIndex = out.add(values[index], x, y, width.toShort(), height.toShort())
  if (owner != null) {
    fillOwner(owner, chunkWidth, x, y, width, height, outIndex)
  }
}

internal fun fillOwner(
  owner: IntArray,
  chunkWidth: Int,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
  index: Int,
) {
  val endX = x + width
  for (yy in y until y + height) {
    val base = yy * chunkWidth
    Arrays.fill(owner, base + x, base + endX, index)
  }
}

internal fun clearOwner(
  owner: IntArray,
  chunkWidth: Int,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
) {
  val endX = x + width
  for (yy in y until y + height) {
    val base = yy * chunkWidth
    Arrays.fill(owner, base + x, base + endX, -1)
  }
}
