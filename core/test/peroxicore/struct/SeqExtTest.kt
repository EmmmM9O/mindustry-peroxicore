package peroxicore.struct

import arc.struct.*
import kotlin.test.*

class SeqExtTest {
  private fun seqOf(vararg values: String): Seq<String> = Seq<String>(values.size).apply {
    values.forEach { add(it) }
  }

  @Test
  fun getOrNullAndFriends() {
    val seq = seqOf("a", "b", "c")

    assertEquals("a", seq.getOrNull(0))
    assertEquals("c", seq.getOrNull(2))
    assertNull(seq.getOrNull(3))
    assertNull(seq.getOrNull(-1))

    assertEquals("a", seq.firstOrNull())
    assertEquals("c", seq.lastOrNull())
    assertEquals(2, seq.lastIndex)
    assertTrue(seq.isNotEmpty)
    assertFalse(Seq<String>().isNotEmpty)
    assertTrue(Seq<String>().isEmpty)

    val empty = Seq<String>()
    assertNull(empty.firstOrNull())
    assertNull(empty.lastOrNull())
    assertEquals(-1, empty.lastIndex)
  }

  @Test
  fun getOrPutAndGrowStillWork() {
    val seq = Seq<String>()

    assertEquals("x", seq.getOrPut(0) { "x" })
    assertEquals(1, seq.size)
    // 已存在时不应覆盖
    assertEquals("x", seq.getOrPut(0) { "y" })

    seq.putOrGrow(3, "d")
    assertEquals(4, seq.size)
    assertNull(seq.getOrNull(1))
    assertEquals("d", seq.getOrNull(3))
  }

  @Test
  fun removeIfKeepsOrder() {
    val seq = Seq<Int>().apply { (0 until 10).forEach { add(it) } }

    val removed = seq.removeIf { it % 3 == 0 }

    assertEquals(4, removed)
    assertEquals(listOf(1, 2, 4, 5, 7, 8), seq.toListCopy())
  }

  @Test
  fun mapAndFilterSeqDoNotMutateSource() {
    val source = seqOf("a", "bb", "ccc")

    val mapped = source.mapSeq { it.length }
    assertEquals(listOf(1, 2, 3), mapped.toListCopy())
    assertEquals(3, source.size, "原 Seq 不应被修改")

    val filtered = source.filterSeq { it.length > 1 }
    assertEquals(listOf("bb", "ccc"), filtered.toListCopy())
    assertEquals(3, source.size)
  }

  @Test
  fun findFirstAndFindLast() {
    val seq = seqOf("aa", "bbb", "cc", "dddd")

    assertEquals("aa", seq.findFirst { it.length > 1 }, "第一个长度大于 1 的元素")
    assertEquals("dddd", seq.findLast { it.length > 1 }, "最后一个长度大于 1 的元素")
    assertNull(seq.findFirst { it == "zz" }, "没有匹配时返回 null")
    assertNull(seq.findFirst { it.length > 10 })
    assertNull(seq.findLast { it.length > 10 })
    assertNull(Seq<String>().findFirst { true })
  }

  @Test
  fun allMatchAndNoneMatch() {
    val seq = seqOf("a", "b")

    assertTrue(seq.allMatch { it.length == 1 })
    assertTrue(seq.noneMatch { it == "z" })
    assertFalse(seq.noneMatch { it == "a" })

    // 空 Seq 的语义与 Kotlin 一致
    assertTrue(Seq<String>().allMatch { false })
    assertTrue(Seq<String>().noneMatch { true })
  }

  @Test
  fun filterNotNullAndReplaceNulls() {
    val withNulls = Seq<String?>(4).apply {
      add("a")
      add(null as String?)
      add("c")
      add(null as String?)
    }

    assertEquals(listOf("a", "c"), withNulls.filterNotNull().toListCopy())
    assertEquals(listOf("a", "?", "c", "?"), withNulls.replaceNulls("?").toListCopy())
  }

  @Test
  fun groupBySeqKeepsOrderAndFirstKeyWinsForAssociate() {
    val seq = seqOf("apple", "banana", "avocado", "blueberry")

    val groups = seq.groupBySeq { it.first() }
    assertEquals(2, groups.size)
    assertEquals(listOf("apple", "avocado"), groups.get('a')!!.toListCopy())
    assertEquals(listOf("banana", "blueberry"), groups.get('b')!!.toListCopy())

    val byLength = seqOf("a", "bb", "cc", "ddd").associateBySeq { it.length }
    assertEquals(3, byLength.size)
    assertEquals("bb", byLength.get(2), "重复键应保留第一个")
    assertEquals("ddd", byLength.get(3))
  }

  @Test
  fun reverseInPlaceAddsNothing() {
    val seq = Seq<Int>().apply { (1..4).forEach { add(it) } }
    assertSame(seq, seq.reverseInPlace())
    assertEquals(listOf(4, 3, 2, 1), seq.toListCopy())

    // 奇数长度
    val odd = Seq<Int>().apply { (1..5).forEach { add(it) } }
    odd.reverseInPlace()
    assertEquals(listOf(5, 4, 3, 2, 1), odd.toListCopy())
  }

  @Test
  fun takeWhileStopsAtCountOrPredicate() {
    val source = Seq<Int>().apply { (0 until 10).forEach { add(it) } }

    val out = Seq<Int>()
    assertEquals(3, source.takeWhile(3, { it < 100 }, out))
    assertEquals(listOf(0, 1, 2), out.toListCopy())

    val stopped = Seq<Int>()
    assertEquals(2, source.takeWhile(100, { it < 2 }, stopped))
    assertEquals(listOf(0, 1), stopped.toListCopy())

    assertFailsWith<IllegalArgumentException> { source.takeWhile(-1, { true }, Seq()) }
  }

  @Test
  fun addAndGetReturnsValue() {
    val seq = Seq<String>()
    assertEquals("hello", seq.addAndGet("hello"))
    assertEquals(1, seq.size)
    assertEquals("hello", seq.get(0))
  }

  @Test
  fun toListCopyIsSnapshot() {
    val seq = seqOf("a", "b")
    val list = seq.toListCopy()

    seq.add("c")

    assertEquals(listOf("a", "b"), list, "快照不应跟着 Seq 变化")
    assertEquals(3, seq.size)
  }
}
