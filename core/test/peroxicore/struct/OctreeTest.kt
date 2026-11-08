package peroxicore.struct

import arc.func.*
import arc.math.geom.*
import arc.struct.*
import kotlin.test.*

class OctreeTest {
  /** 测试用的球体对象：包围盒紧贴球体。 */
  private class Ball(val center: Vec3, val radius: Float) : Octree.OctreeObject {
    override fun hitbox(out: BoundingBox) {
      out.set(
        Vec3(center.x - radius, center.y - radius, center.z - radius),
        Vec3(center.x + radius, center.y + radius, center.z + radius),
      )
    }
  }

  private fun tree(size: Float = 100f): Octree<Ball> =
    Octree(BoundingBox(Vec3(0f, 0f, 0f), Vec3(size, size, size)))

  private fun ball(x: Float, y: Float, z: Float, r: Float = 1f) = Ball(Vec3(x, y, z), r)

  private fun boxAt(x: Float, y: Float, z: Float, half: Float = 2f): BoundingBox =
    BoundingBox(Vec3(x - half, y - half, z - half), Vec3(x + half, y + half, z + half))

  /** 返回对象的球半径。 */
  private val radiusOf = Floatf { b: Ball -> b.radius }

  // ------------------------------------------------------------------ 结构

  @Test
  fun insertSplitsAndCounts() {
    val tree = tree()
    val balls = (0 until 50).map { i -> ball(5f + (i % 10) * 9f, 5f + (i / 10) * 18f, 5f + (i % 3) * 30f) }

    balls.forEach { tree.insert(it) }

    assertFalse(tree.leaf, "root should have split")
    assertEquals(balls.size, tree.totalObjects)

    val all = Seq<Ball>()
    tree.getObjects(all)
    assertEquals(balls.size, all.size, "every inserted object must be reachable")
    balls.forEach { assertTrue(all.contains(it, true), "missing object") }
  }

  @Test
  fun outOfBoundsObjectsAreIgnored() {
    val tree = tree()
    tree.insert(ball(-50f, 10f, 10f))
    assertEquals(0, tree.totalObjects)

    val out = Seq<Ball>()
    tree.getObjects(out)
    assertEquals(0, out.size)
  }

  @Test
  fun removeKeepsTreeConsistent() {
    val tree = tree()
    val balls = (0 until 40).map { i -> ball(4f + (i % 8) * 12f, 4f + ((i / 8) % 4) * 24f, 4f + ((i / 32) % 3) * 40f) }
    balls.forEach { tree.insert(it) }

    val removed = balls.filterIndexed { i, _ -> i % 3 == 0 }
    removed.forEach { assertTrue(tree.remove(it), "remove should succeed") }
    assertFalse(tree.remove(removed.first()), "removing twice should fail")

    assertEquals(balls.size - removed.size, tree.totalObjects)

    val all = Seq<Ball>()
    tree.getObjects(all)
    assertEquals(balls.size - removed.size, all.size)
    removed.forEach { assertFalse(all.contains(it, true), "removed object still present") }

    // 删空之后应当收回子节点
    balls.filterNot { removed.contains(it) }.forEach { tree.remove(it) }
    assertEquals(0, tree.totalObjects)
    assertTrue(tree.leaf, "tree should collapse back to a leaf")
  }

  @Test
  fun clearEmptiesEverything() {
    val tree = tree()
    (0 until 30).forEach { i -> tree.insert(ball(5f + i * 3f, 50f, 50f)) }
    tree.clear()

    assertEquals(0, tree.totalObjects)
    val out = Seq<Ball>()
    tree.getObjects(out)
    assertEquals(0, out.size)
    assertTrue(tree.leaf)
    assertFalse(tree.any(boxAt(50f, 50f, 50f, 40f)))
  }

  // ------------------------------------------------------------------ 范围查询

  @Test
  fun intersectNeverMissesObjects() {
    val tree = tree()
    val balls = (0 until 6).flatMap { x ->
      (0 until 6).flatMap { y ->
        (0 until 4).map { z -> ball(8f + x * 16f, 8f + y * 16f, 10f + z * 20f, 2f) }
      }
    }
    balls.forEach { tree.insert(it) }

    val query = boxAt(40f, 40f, 50f, 24f)
    val expected = balls.filter { b -> boxAt(b.center.x, b.center.y, b.center.z, b.radius).intersects(query) }
    assertTrue(expected.size < balls.size, "query should not trivially cover everything")

    val out = Seq<Ball>()
    tree.intersect(query, out)
    expected.forEach { assertTrue(out.contains(it, true), "missing candidate") }

    assertTrue(tree.any(query))
    assertFalse(tree.any(boxAt(500f, 500f, 500f)), "far away box must not report hits")
    assertNotNull(tree.find(query) { it.radius >= 2f })

    var stopped = false
    assertTrue(tree.intersect(query, Boolf { stopped = true; true }))
    assertTrue(stopped)

    val viaCons = Seq<Ball>()
    tree.intersect(query, Cons { viaCons.add(it) })
    assertEquals(out.size, viaCons.size)
  }

  @Test
  fun intersectFindsCrossingObjectsStoredInParent() {
    val tree = tree()
    // 跨越子节点边界的对象会挂在内部节点上，不能被漏掉
    val big = ball(50f, 50f, 50f, 30f)
    tree.insert(big)
    (0 until 20).forEach { i -> tree.insert(ball(5f + i * 4f, 5f, 5f, 1f)) }

    assertFalse(tree.leaf)
    val query = boxAt(50f, 50f, 50f, 5f)
    val out = Seq<Ball>()
    tree.intersect(query, out)
    assertTrue(out.contains(big, true), "object stored in the parent node must be found")
  }

  // ------------------------------------------------------------------ 射线检测

  @Test
  fun raycastHitsNearestSphereFirst() {
    val tree = tree()
    (0 until 9).forEach { i -> tree.insert(ball(10f + i * 10f, 50f, 50f, 3f)) }

    val hits = Seq<Octree.RayHit<Ball>>()
    tree.raycast(Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f)), 1000f, radiusOf, hits)

    assertEquals(9, hits.size, "all spheres along the ray should be hit")
    for (i in 1 until hits.size) {
      assertTrue(hits.get(i - 1).dst <= hits.get(i).dst, "hits must be sorted by distance")
    }
    assertEquals(7f, hits.first().dst, 1e-3f, "first hit distance")
  }

  @Test
  fun raycastRespectsLengthAndDirection() {
    val tree = tree()
    tree.insert(ball(10f, 50f, 50f, 3f))
    tree.insert(ball(80f, 50f, 50f, 3f))

    val shortHits = Seq<Octree.RayHit<Ball>>()
    tree.raycast(Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f)), 20f, radiusOf, shortHits)
    assertEquals(1, shortHits.size)
    assertEquals(7f, shortHits.first().dst, 1e-3f)

    val backHits = Seq<Octree.RayHit<Ball>>()
    tree.raycast(Ray(Vec3(0f, 50f, 50f), Vec3(-1f, 0f, 0f)), 1000f, radiusOf, backHits)
    assertEquals(0, backHits.size, "ray pointing away must not hit")

    val first = tree.raycastFirst(Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f)), 1000f, radiusOf)
    assertNotNull(first)
    assertEquals(7f, first.dst, 1e-3f)
    assertNotNull(first.obj)
  }

  @Test
  fun raycastFirstMatchesRaycastWithoutAllocating() {
    val tree = tree()
    val random = kotlin.random.Random(777)
    (0 until 150).forEach {
      tree.insert(Ball(Vec3(random.nextFloat() * 100f, random.nextFloat() * 100f, random.nextFloat() * 100f), 0.5f + random.nextFloat() * 3f))
    }

    repeat(40) {
      val origin = Vec3(random.nextFloat() * 100f, random.nextFloat() * 100f, random.nextFloat() * 100f)
      val dir = Vec3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).nor()
      val ray = Ray(origin, dir)

      val all = Seq<Octree.RayHit<Ball>>()
      tree.raycast(ray, 400f, radiusOf, all)
      val first = tree.raycastFirst(ray, 400f, radiusOf)

      if (all.size == 0) {
        assertNull(first, "raycast 没有命中时 raycastFirst 也不应命中")
      } else {
        assertNotNull(first)
        assertEquals(all.first().dst, first.dst, 1e-3f, "两者应当给出同一个最近命中")
        assertSame(all.first().obj, first.obj)
      }
    }
  }

  @Test
  fun raycastFirstAcrossBranchesPicksGlobalNearest() {
    val tree = tree()
    // 刻意让较远的球在较近的球之前被遍历到（分属不同分支）
    tree.insert(ball(80f, 50f, 50f, 3f))
    (0 until 20).forEach { i -> tree.insert(ball(5f + i * 2f, 5f, 5f, 1f)) }
    tree.insert(ball(10f, 50f, 50f, 3f))

    val ray = Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f))
    val first = tree.raycastFirst(ray, 1000f, radiusOf)
    assertNotNull(first)
    assertEquals(7f, first.dst, 1e-3f, "应当返回全局最近的命中，而不是某个分支里的局部最近")

    val all = Seq<Octree.RayHit<Ball>>()
    tree.raycast(ray, 1000f, radiusOf, all)
    assertEquals(all.first().dst, first.dst, 1e-3f)
  }

  @Test
  fun raycastIgnoresNonPositiveRadius() {
    val tree = tree()
    tree.insert(ball(10f, 50f, 50f, 3f))

    val hits = Seq<Octree.RayHit<Ball>>()
    tree.raycast(Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f)), 1000f, Floatf { 0f }, hits)
    assertEquals(0, hits.size)
  }

  @Test
  fun raycastMatchesBruteForce() {
    val tree = tree()
    val random = kotlin.random.Random(1234)
    val balls = (0 until 200).map {
      Ball(Vec3(random.nextFloat() * 100f, random.nextFloat() * 100f, random.nextFloat() * 100f), 0.5f + random.nextFloat() * 3f)
    }
    balls.forEach { tree.insert(it) }

    repeat(50) {
      val origin = Vec3(random.nextFloat() * 100f, random.nextFloat() * 100f, random.nextFloat() * 100f)
      val dir = Vec3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).nor()
      val ray = Ray(origin, dir)

      val hits = Seq<Octree.RayHit<Ball>>()
      tree.raycast(ray, 400f, radiusOf, hits)

      val brute = balls.filter { Intersector3D.intersectRaySphere(ray, it.center, it.radius, null) }
      assertEquals(brute.size, hits.size, "raycast must not miss or duplicate")
      brute.forEach { b -> assertTrue(hits.any { it.obj === b }, "brute force hit not reported") }
    }
  }

  @Test
  fun pathcastClampsInsideSphere() {
    val tree = tree()

    val inside = tree.pathcast(Ray(Vec3(10f, 50f, 50f), Vec3(1f, 0f, 0f)), 100f, Vec3(10f, 50f, 50f), 3f)
    assertNotNull(inside)
    assertEquals(0f, inside.dst, 1e-3f)
    assertNull(inside.obj)

    val normal = tree.pathcast(Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f)), 100f, Vec3(10f, 50f, 50f), 3f)
    assertNotNull(normal)
    assertEquals(7f, normal.dst, 1e-3f)

    assertNull(tree.pathcast(Ray(Vec3(0f, 50f, 50f), Vec3(1f, 0f, 0f)), 5f, Vec3(10f, 50f, 50f), 3f))
    assertNull(tree.pathcast(Ray(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)), 100f, Vec3(10f, 50f, 50f), 3f))
  }
}
