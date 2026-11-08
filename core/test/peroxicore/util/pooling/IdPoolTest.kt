package peroxicore.util.pooling

import kotlin.test.*

class IdPoolTest {
  /** 测试用载体，记录 reset 次数。 */
  private class Item : IdPoolId<String> {
    override var id: Int = -1
    override var origin: String? = null

    var resets = 0
      private set

    override fun reset() {
      resets++
      origin = null
    }
  }

  private fun pool(capacity: Int = 4): IdPool<Item, String> = IdPool(Item::class.java, { Item() }, capacity)

  @Test
  fun putAssignsSequentialIds() {
    val pool = pool()
    val a = pool.put("a")
    val b = pool.put("b")
    val c = pool.put("c")

    assertEquals(0, a.id)
    assertEquals(1, b.id)
    assertEquals(2, c.id)
    assertEquals(3, pool.size)
    assertEquals(3, pool.capacity)
    assertEquals("b", b.origin, "origin 应当被写入载体")
  }

  @Test
  fun freedIdIsReused() {
    val pool = pool()
    val a = pool.put("a")
    val b = pool.put("b")

    pool.free(a)
    assertEquals(-1, a.id, "释放后 id 应当被清掉")
    assertEquals(1, pool.size)
    assertEquals(1, pool.free)

    val c = pool.put("c")
    assertEquals(0, c.id, "被释放的 id 应当被复用")
    assertSame(a, c, "载体本身也应当被复用")
    assertEquals("c", c.origin, "复用后 origin 应当更新")
    assertEquals(2, pool.size)
  }

  @Test
  fun smallestFreeIdIsReusedFirst() {
    val pool = pool()
    val items = (0 until 5).map { pool.put("v" + it) }
    // 释放 3、1、2，下一次应当拿到 1
    listOf(3, 1, 2).forEach { pool.free(items[it]) }

    assertEquals(1, pool.put("x").id)
    assertEquals(2, pool.put("y").id)
    assertEquals(3, pool.put("z").id)
  }

  @Test
  fun freeResetsCarrier() {
    val pool = pool()
    val a = pool.put("data")
    assertEquals(0, pool.free, "分配后没有空闲 id")

    pool.free(a)
    assertEquals(1, a.resets, "归还时应当调用 reset")
    assertNull(a.origin)
    assertEquals(1, pool.free, "归还后应当有一个空闲 id")
  }

  @Test
  fun doubleFreeIsRejected() {
    val pool = pool()
    val a = pool.put("a")
    pool.free(a)
    assertFailsWith<IllegalArgumentException> { pool.free(a) }
  }

  @Test
  fun foreignObjectIsRejected() {
    val pool = pool()
    val foreign = Item()
    assertFailsWith<IllegalArgumentException> { pool.free(foreign) }
  }

  @Test
  fun capacityShrinksWhenTopIdFreed() {
    val pool = pool()
    val a = pool.put("a")
    pool.put("b")
    val c = pool.put("c")
    assertEquals(3, pool.capacity)

    pool.free(c)
    assertEquals(1, pool.free, "释放的 id 应当进入空闲列表")
    pool.free(a)
    assertEquals(2, pool.free)

    // 空闲 id 优先复用：最小的先拿
    assertEquals(0, pool.put("d").id)
    assertEquals(2, pool.put("e").id)
    assertEquals(3, pool.size, "活跃对象数：b、d、e")
    assertEquals(0, pool.free)
  }

  @Test
  fun getAndIsActive() {
    val pool = pool()
    val a = pool.put("a")
    assertEquals(a, pool.get(0))
    assertSame(a, pool.get(0))
    assertTrue(pool.isActive(0))
    assertNull(pool.get(5))
    assertNull(pool.get(-1))
    assertFalse(pool.isActive(5))

    pool.free(a)
    assertNull(pool.get(0))
    assertFalse(pool.isActive(0))
  }

  @Test
  fun eachVisitsActiveObjectsOnly() {
    val pool = pool()
    val items = (0 until 4).map { pool.put("v" + it) }
    pool.free(items[1])
    pool.free(items[3])

    val seen = mutableListOf<Item>()
    pool.each { seen.add(it) }
    assertEquals(listOf(items[0], items[2]), seen)
  }

  @Test
  fun growsPastInitialCapacity() {
    val pool = pool(capacity = 1)
    val items = (0 until 100).map { pool.put("v" + it) }
    assertEquals(100, pool.size)
    assertEquals(99, items.last().id)

    // 全部释放后应当能重新从 0 开始分配，且载体被复用
    items.forEach { pool.free(it) }
    assertEquals(0, pool.size)
    assertEquals(100, pool.free)

    val again = pool.put("again")
    assertEquals(0, again.id)
    assertSame(items[0], again, "载体实例应当被复用")
  }

  @Test
  fun clearDropsEverything() {
    val pool = pool()
    (0 until 5).forEach { pool.put("v" + it) }
    pool.clear()

    assertEquals(0, pool.size)
    assertEquals(0, pool.capacity)
    assertEquals(0, pool.free)
    assertNull(pool.get(0))
    assertEquals(0, pool.put("fresh").id)
  }

  @Test
  fun fuzzNeverAllocatesActiveIdTwice() {
    val pool = pool(capacity = 2)
    val random = kotlin.random.Random(42)
    val active = mutableMapOf<Int, Item>()

    repeat(2000) {
      if (active.isNotEmpty() && random.nextBoolean()) {
        val id = active.keys.random(random)
        val item = active.remove(id)!!
        pool.free(item)
      } else {
        val item = pool.put("v" + it)
        assertFalse(active.containsKey(item.id), "id " + item.id + " 被同时分配给了两个对象")
        active[item.id] = item
      }
      assertEquals(active.size, pool.size)
      active.forEach { (id, item) -> assertSame(item, pool.get(id)) }
    }
  }
}
