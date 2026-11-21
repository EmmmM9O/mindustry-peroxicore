package peroxicore.struct

import arc.struct.*
import kotlin.test.*

class ObjectMapExtTest {
  @Test
  fun `build and index access`() {
    val map = objectMapOf("a" to 1, "b" to 2)
    assertEquals(2, map.size)

    val built = buildObjectMap<String, Int> {
      this["c"] = 3
      put("d", 4)
    }
    assertEquals(3, built["c"])
    assertEquals(4, built["d"])
    assertTrue("c" in built)
    assertFalse("x" in built)

    built += "e" to 5
    assertEquals(5, built["e"])
  }

  @Test
  fun `destructuring entries`() {
    val map = objectMapOf("a" to 1)
    for ((key, value) in map.entries()) {
      assertEquals("a", key)
      assertEquals(1, value)
    }
  }

  @Test
  fun `get or else and get or put`() {
    val map = objectMapOf("a" to 1)
    assertEquals(1, map.getOrElse("a") { 99 })
    assertEquals(99, map.getOrElse("b") { 99 })
    assertFalse(map.containsKey("b"))

    assertEquals(1, map.getOrPut("a") { 99 })
    assertEquals(7, map.getOrPut("c") { 7 })
    assertEquals(7, map["c"])
  }

  @Test
  fun `putIfAbsent keeps old value`() {
    val map = objectMapOf("a" to 1)
    assertEquals(1, map.putIfAbsent("a", 2))
    assertEquals(1, map["a"])
    assertNull(map.putIfAbsent("b", 3))
    assertEquals(3, map["b"])
  }

  @Test
  fun `null value is treated as absent`() {
    val map = ObjectMap<String, Int?>()
    map.put("a", null)
    assertNull(map.getOrNull("a"))
    assertNull(map.getOrNull(null))
    assertEquals(5, map.getOrElse("a") { 5 })
  }

  @Test
  fun `putPairs`() {
    val map = ObjectMap<String, Int>()
    map.putPairs("a" to 1, "b" to 2)
    map.putPairs(listOf("c" to 3))
    assertEquals(3, map.size)
    assertEquals(2, map.putPair("b" to 22))
    assertEquals(22, map["b"])
  }

  @Test
  fun `iteration helpers`() {
    val map = objectMapOf("a" to 1, "b" to 2, "c" to 3)

    val seen = ArrayList<String>()
    map.forEachEntry { key, value -> seen.add("$key$value") }
    assertEquals(3, seen.size)

    val found = map.findEntry { key, _ -> key == "b" }
    assertNotNull(found)
    assertEquals(2, found.value)

    assertNull(map.findEntry { _, value -> value == 99 })
    assertNotNull(map.firstEntryOrNull())
    assertNull(ObjectMap<String, Int>().firstEntryOrNull())
  }

  @Test
  fun `removeIf and retainIf`() {
    val map = objectMapOf("a" to 1, "b" to 2, "c" to 3)
    val removed = map.removeIf { _, value -> value % 2 == 1 }
    assertEquals(2, removed)
    assertEquals(1, map.size)
    assertEquals(2, map["b"])

    map.retainIf { _, _ -> false }
    assertTrue(map.isEmpty())
  }

  @Test
  fun `map and filter entries`() {
    val map = objectMapOf("a" to 1, "b" to 2, "c" to 3)
    val mapped = map.mapEntries { key, value -> "$key$value" }
    assertEquals(3, mapped.size)

    val filtered = map.filterEntries { _, value -> value > 1 }
    assertEquals(2, filtered.size)
    assertFalse(filtered.containsKey("a"))
    assertEquals(3, map.size)

    assertEquals(3, map.keysSeq().size)
    assertEquals(3, map.valuesSeq().size)
  }

  @Test
  fun `removeIf handles stash entries`() {
    // 大量键制造碰撞/溢出，确保 stash 内的元素也能被正确移除
    val map = ObjectMap<Int, Int>()
    for (i in 0 until 512) map.put(i, i)
    val removed = map.removeIf { _, value -> value % 3 == 0 }
    assertEquals(171, removed)
    assertEquals(341, map.size)
    for (i in 0 until 512) {
      if (i % 3 == 0) assertFalse(map.containsKey(i)) else assertEquals(i, map[i])
    }
  }
}
