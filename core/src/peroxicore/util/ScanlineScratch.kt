package peroxicore.util

internal class ScanlineScratch(capacity: Int) {
  val prevX = IntArray(capacity)
  val prevWidth = IntArray(capacity)
  val prevData = ShortArray(capacity)
  val prevStartY = IntArray(capacity)
  val curX = IntArray(capacity)
  val curWidth = IntArray(capacity)
  val curData = ShortArray(capacity)
  val curStartY = IntArray(capacity)
}
