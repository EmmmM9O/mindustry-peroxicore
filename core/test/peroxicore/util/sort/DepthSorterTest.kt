package peroxicore.util.sort

import arc.struct.*
import kotlin.test.*

class DepthSorterTest {
  private class Item(override val sortKey: Int) : DepthItem {
    override val z: Float get() = sortKey.toFloat()
    override fun toString(): String = "Item($sortKey)"
  }

  /** 用一个共享的序号记录原始顺序，便于检查稳定性。 */
  private fun items(vararg keys: Int): Seq<Item> = Seq<Item>(keys.size).apply {
    keys.forEach { add(Item(it)) }
  }

  private fun snapshot(seq: Seq<Item>): List<Item> {
    val out = ArrayList<Item>(seq.size)
    for (i in 0 until seq.size) out.add(seq.get(i))
    return out
  }

  private fun keysOf(seq: Seq<Item>): List<Int> = snapshot(seq).map { it.sortKey }

  /** 校验：结果有序，且相同键之间的相对顺序与排序前一致。 */
  private fun assertStableSorted(before: Seq<Item>, after: Seq<Item>) {
    assertEquals(before.size, after.size)

    // 有序
    for (i in 1 until after.size) {
      assertTrue(after.get(i - 1).sortKey <= after.get(i).sortKey, "结果应当有序")
    }

    // 稳定：同键对象在 before 中的出现顺序应当与 after 中一致
    val beforeList = snapshot(before)
    val afterList = snapshot(after)
    val beforeOrder = beforeList.withIndex().groupBy { it.value.sortKey }.mapValues { e -> e.value.map { it.index } }
    val afterByKey = afterList.groupBy { it.sortKey }
    for ((key, group) in afterByKey) {
      val original = beforeOrder[key]!!
      val positions = group.map { item -> beforeList.indexOfFirst { it === item } }
      assertEquals(original, positions, "键 " + key + " 的相对顺序应当保持不变")
    }
  }

  private fun checkSorter(sorter: DepthSorter<Item>) {
    val cases = listOf(
      intArrayOf(),
      intArrayOf(1),
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(5, 4, 3, 2, 1),
      intArrayOf(3, 1, 2, 1, 3, 2),
      intArrayOf(1, 1, 1, 1, 1),
      intArrayOf(9, 1, 9, 1, 9, 1, 9, 1),
    )

    for (case in cases) {
      val seq = Seq<Item>(case.size)
      case.forEach { seq.add(Item(it)) }
      // 排序前的快照必须引用同一批对象，否则身份比较没有意义
      val before = Seq<Item>(case.size)
      for (i in 0 until seq.size) before.add(seq.get(i))

      val result = sorter.sort(seq)
      assertSame(seq, result, "sort 应当返回同一个 Seq")
      assertStableSorted(before, result)
    }
  }

  @Test
  fun countingSorterIsStableAndCorrect() = checkSorter(CountingDepthSorter())

  @Test
  fun insertionSorterIsStableAndCorrect() = checkSorter(InsertionDepthSorter())

  @Test
  fun comparatorSorterIsStableAndCorrect() = checkSorter(DualPivotDepthSorter())

  @Test
  fun nullSorterKeepsOrder() {
    val seq = items(5, 3, 1)
    val sorter = NullDepthSorter<Item>()
    sorter.sort(seq)
    assertEquals(listOf(5, 3, 1), keysOf(seq))
  }

  @Test
  fun countingSorterMatchesReferenceOnRandomData() {
    val random = kotlin.random.Random(99)
    repeat(50) {
      val size = random.nextInt(0, 400)
      val seq = Seq<Item>()
      for (i in 0 until size) seq.add(Item(random.nextInt(-50, 50)))

      val expected = snapshot(seq).sortedBy { it.sortKey }
      CountingDepthSorter<Item>().sort(seq)
      assertEquals(expected.map { it.sortKey }, keysOf(seq))
    }
  }

  @Test
  fun countingSorterHandlesManyDistinctKeys() {
    // 超过插入排序阈值，走计数排序分支
    val seq = Seq<Item>()
    for (i in 0 until 500) seq.add(Item(500 - i))
    CountingDepthSorter<Item>().sort(seq)
    for (i in 1 until seq.size) {
      assertTrue(seq.get(i - 1).sortKey <= seq.get(i).sortKey)
    }
  }

  @Test
  fun forEachDrawDoesNotMutateInput() {
    val seq = items(3, 1, 2)
    val visited = mutableListOf<Int>()
    CountingDepthSorter<Item>().forEachDraw(seq) { visited.add(it.sortKey) }

    val expected = snapshot(seq).sortedBy { it.sortKey }.map { it.sortKey }
    assertEquals(expected, visited)
    assertEquals(listOf(3, 1, 2), keysOf(seq), "forEachDraw 不应修改原顺序")
  }

  @Test
  fun bufferIsReusedAcrossCalls() {
    val sorter = CountingDepthSorter<Item>(16)
    val first = Seq<Item>().apply { (0 until 100).forEach { add(Item(100 - it)) } }
    sorter.sort(first)
    val sizeAfterFirst = sorter.bufferSize
    assertTrue(sizeAfterFirst > 0)

    val second = Seq<Item>().apply { (0 until 100).forEach { add(Item(100 - it)) } }
    sorter.sort(second)
    assertEquals(sizeAfterFirst, sorter.bufferSize, "缓冲区应当复用而不是每次重建")
  }

  @Test
  fun factoryHelpers() {
    assertTrue(DepthSorters.counting<Item>() is CountingDepthSorter<*>)
    assertTrue(DepthSorters.insertion<Item>() is InsertionDepthSorter<*>)
    assertTrue(DepthSorters.none<Item>() is NullDepthSorter<*>)
    assertTrue(DepthSorters.bySize<Item>(10) is InsertionDepthSorter<*>)
    assertTrue(DepthSorters.bySize<Item>(1000) is CountingDepthSorter<*>)
    assertTrue(DepthSorters.comparing { a: Item, b: Item -> a.sortKey.compareTo(b.sortKey) } is DualPivotDepthSorter<*>)

    val shared = items(2, 1)
    DepthSorters.sort(shared)
    assertEquals(listOf(1, 2), keysOf(shared))
  }

  @Test
  fun customComparatorOrdersByKeyDescending() {
    val seq = items(1, 3, 2)
    // 工厂顺序：z 值大的先绘制（这里只是验证比较器生效）
    DepthSorters.comparing<Item> { a, b -> b.sortKey.compareTo(a.sortKey) }.sort(seq)
    assertEquals(listOf(3, 2, 1), keysOf(seq))
  }
}
