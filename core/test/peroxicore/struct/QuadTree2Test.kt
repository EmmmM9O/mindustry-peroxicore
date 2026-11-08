package peroxicore.struct

import arc.math.geom.*
import arc.struct.*
import kotlin.test.*

class QuadTree2Test {
  /** 测试用矩形对象。 */
  private class Box(val rect: Rect) : QuadTree2.QuadTreeObject {
    override fun hitbox(out: Rect) {
      out.set(rect)
    }

    override fun toString(): String = "Box(" + rect.x + "," + rect.y + "," + rect.width + "," + rect.height + ")"
  }

  private fun box(x: Float, y: Float, w: Float, h: Float) = Box(Rect(x, y, w, h))

  private fun tree(w: Float = 100f, h: Float = 100f): QuadTree2<Box> = QuadTree2(Rect(0f, 0f, w, h))

  /** 构造一棵包含各种相对位置关系的树。 */
  private fun sample(): QuadTree2<Box> {
    val tree = tree()
    (0 until 10).forEach { x ->
      (0 until 10).forEach { y ->
        tree.insert(box(x * 9f + 1f, y * 9f + 1f, 2f, 2f))
      }
    }
    tree.insert(box(40f, 40f, 20f, 20f))
    tree.insert(box(0f, 0f, 100f, 100f))
    tree.insert(box(60f, 60f, 10f, 10f))
    tree.insert(box(0f, 0f, 10f, 10f))
    return tree
  }

  @Test
  fun insertAndRemoveKeepCounts() {
    val tree = tree()
    val boxes = (0 until 20).map { box(it * 4f, it * 4f, 2f, 2f) }
    boxes.forEach { tree.insert(it) }
    assertEquals(20, tree.totalObjects)

    val out = Seq<Box>()
    tree.getObjects(out)
    assertEquals(20, out.size)

    boxes.take(5).forEach { assertTrue(tree.remove(it), "remove 应当成功") }
    assertEquals(15, tree.totalObjects)

    val remaining = Seq<Box>()
    tree.getObjects(remaining)
    assertEquals(15, remaining.size)
    boxes.take(5).forEach { assertFalse(remaining.contains(it, true)) }
  }

  @Test
  fun overlapFindsEveryIntersection() {
    val tree = sample()
    val query = Rect(35f, 35f, 30f, 30f)

    val out = Seq<Box>()
    tree.query(query, QueryMode.Overlap, out)

    val all = Seq<Box>()
    tree.getObjects(all)
    val expected = all.filter { it.rect.overlaps(query) }

    expected.forEach { assertTrue(out.contains(it, true), "漏掉了 ") }
    assertEquals(expected.size, out.size, "Overlap 不应产生误报")
  }

  @Test
  fun coveredByReturnsOnlyFullyContained() {
    val tree = sample()
    val query = Rect(30f, 30f, 40f, 40f)

    val out = Seq<Box>()
    tree.coveredBy(query, out)

    assertTrue(out.isNotEmpty)
    out.forEach {
      assertTrue(
        it.rect.x >= query.x && it.rect.x + it.rect.width <= query.x + query.width &&
          it.rect.y >= query.y && it.rect.y + it.rect.height <= query.y + query.height,
        "不应被算作被完整覆盖",
      )
    }
    assertFalse(out.any { it.rect.width == 100f })
    assertTrue(out.any { it.rect.width == 20f })
  }

  @Test
  fun coveringReturnsOnlyObjectsThatContainQuery() {
    val tree = sample()
    val query = Rect(45f, 45f, 5f, 5f)

    val out = Seq<Box>()
    tree.covering(query, out)

    assertTrue(out.isNotEmpty)
    out.forEach {
      assertTrue(
        it.rect.x <= query.x && it.rect.x + it.rect.width >= query.x + query.width &&
          it.rect.y <= query.y && it.rect.y + it.rect.height >= query.y + query.height,
        "不应被算作覆盖查询",
      )
    }
    assertTrue(out.any { it.rect.width == 100f })
    assertTrue(out.any { it.rect.width == 20f })
  }

  @Test
  fun crossingIsNeitherContainedNorContaining() {
    val tree = sample()
    val query = Rect(30f, 30f, 40f, 40f)

    val out = Seq<Box>()
    tree.crossing(query, out)

    val contained = Seq<Box>()
    tree.coveredBy(query, contained)
    val covering = Seq<Box>()
    tree.covering(query, covering)

    out.forEach {
      assertFalse(contained.any { c -> c === it }, "Crossing 不应包含被完整覆盖的对象")
      assertFalse(covering.any { c -> c === it }, "Crossing 不应包含覆盖查询的对象")
    }
  }

  @Test
  fun touchingDetectsEdgeContact() {
    val tree = tree()
    val a = box(10f, 10f, 10f, 10f)
    val c = box(40f, 40f, 10f, 10f)
    tree.insert(a)
    tree.insert(c)

    val out = Seq<Box>()
    tree.touching(Rect(10f, 10f, 10f, 10f), out)
    // 与查询完全重合意味着内部也重叠，因此不算"只碰边界"
    assertFalse(out.any { it === a }, "完全重叠不应被判为 Touching")
    assertFalse(out.any { it === c }, "完全不相交的对象不应命中")

    // 只贴一条边：内部不重叠，算 Touching
    val edge = box(20f, 10f, 10f, 10f)
    tree.insert(edge)
    val edgeOut = Seq<Box>()
    tree.touching(Rect(10f, 10f, 10f, 10f), edgeOut)
    assertTrue(edgeOut.any { it === edge }, "只贴边应当被判为 Touching")
  }

  @Test
  fun equalsFindsExactMatch() {
    val tree = tree()
    val a = box(10f, 10f, 10f, 10f)
    tree.insert(a)
    tree.insert(box(10f, 10f, 11f, 10f))

    val out = Seq<Box>()
    tree.equalsTo(Rect(10f, 10f, 10f, 10f), out)
    assertEquals(1, out.size)
    assertSame(a, out.first())
  }

  @Test
  fun disjointIsTheComplementOfOverlap() {
    val tree = sample()
    val query = Rect(35f, 35f, 30f, 30f)

    val overlapping = Seq<Box>()
    tree.overlap(query, overlapping)

    val disjoint = Seq<Box>()
    tree.disjoint(query, disjoint)

    val all = Seq<Box>()
    tree.getObjects(all)

    assertEquals(all.size, overlapping.size + disjoint.size, "Overlap 与 Disjoint 应当互补")
    disjoint.forEach { assertFalse(overlapping.contains(it, true)) }
  }

  @Test
  fun anyFindAndCountAgree() {
    val tree = sample()
    val query = Rect(30f, 30f, 40f, 40f)

    assertTrue(tree.any(query))
    assertNotNull(tree.find(query))

    val out = Seq<Box>()
    tree.query(query, QueryMode.Overlap, out)
    assertEquals(out.size, tree.count(query))

    val empty = Rect(1000f, 1000f, 1f, 1f)
    assertFalse(tree.any(empty))
    assertNull(tree.find(empty))
    assertEquals(0, tree.count(empty))
  }

  @Test
  fun queriesMatchBruteForceForRandomLayouts() {
    val random = kotlin.random.Random(2024)
    val tree = tree()
    val boxes = (0 until 300).map {
      box(random.nextFloat() * 100f, random.nextFloat() * 100f, 0.5f + random.nextFloat() * 6f, 0.5f + random.nextFloat() * 6f)
    }
    boxes.forEach { tree.insert(it) }

    repeat(30) {
      val query = Rect(random.nextFloat() * 100f, random.nextFloat() * 100f, 1f + random.nextFloat() * 30f, 1f + random.nextFloat() * 30f)

      for (mode in QueryMode.entries) {
        val out = Seq<Box>()
        tree.query(query, mode, out)

        val expected = boxes.filter { bruteForce(it.rect, query, mode) }
        assertEquals(expected.size, out.size, "模式 " + mode + " 的数量不匹配")
        expected.forEach { b -> assertTrue(out.contains(b, true), "模式 " + mode + " 漏掉了对象") }
      }
    }
  }

  /** 与实现无关的参考判定，用来校验树。 */
  private fun bruteForce(a: Rect, q: Rect, mode: QueryMode): Boolean {
    val ax2 = a.x + a.width
    val ay2 = a.y + a.height
    val qx2 = q.x + q.width
    val qy2 = q.y + q.height
    val overlap = a.x <= qx2 && ax2 >= q.x && a.y <= qy2 && ay2 >= q.y
    if (!overlap) return mode == QueryMode.Disjoint

    val contained = a.x >= q.x && ax2 <= qx2 && a.y >= q.y && ay2 <= qy2
    val covering = a.x <= q.x && ax2 >= qx2 && a.y <= q.y && ay2 >= qy2
    val strict = a.x < qx2 && ax2 > q.x && a.y < qy2 && ay2 > q.y
    return when (mode) {
      QueryMode.Overlap -> true
      QueryMode.Contains -> contained
      QueryMode.ContainedBy -> covering
      QueryMode.Crossing -> !contained && !covering
      QueryMode.Equals -> contained && covering
      QueryMode.Touching -> !strict
      QueryMode.Disjoint -> false
    }
  }

  @Test
  fun queryCircleFiltersByRealDistance() {
    val tree = tree()
    val near = box(45f, 45f, 10f, 10f)
    val corner = box(70f, 70f, 10f, 10f)
    tree.insert(near)
    tree.insert(corner)

    val out = Seq<Box>()
    tree.queryCircle(50f, 50f, 10f, out)

    assertTrue(out.contains(near, true))
    assertFalse(out.contains(corner, true), "圆外但外接正方形内的对象不应命中")
  }

  @Test
  fun nearestReturnsClosestFirst() {
    val tree = tree()
    val a = box(10f, 10f, 2f, 2f)
    val b = box(50f, 50f, 2f, 2f)
    val c = box(90f, 90f, 2f, 2f)
    tree.insert(a)
    tree.insert(b)
    tree.insert(c)

    val out = Seq<QuadTree2.Nearest<Box>>()
    assertEquals(2, tree.nearest(12f, 12f, 2, out))
    assertSame(a, out.get(0).obj)
    assertSame(b, out.get(1).obj)
    assertTrue(out.get(0).dst2 <= out.get(1).dst2)
  }
}
