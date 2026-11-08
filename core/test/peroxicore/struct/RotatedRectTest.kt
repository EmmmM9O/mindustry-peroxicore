package peroxicore.struct

import arc.math.geom.*
import arc.struct.*
import kotlin.math.*
import kotlin.test.*

class RotatedRectTest {
  @Test
  fun zeroRotationBoundsEqualRect() {
    val rot = RotatedRect(50f, 50f, 20f, 10f, 0f)
    val box = Rect()
    rot.bounds(box)
    assertEquals(40f, box.x, 1e-4f)
    assertEquals(45f, box.y, 1e-4f)
    assertEquals(20f, box.width, 1e-4f)
    assertEquals(10f, box.height, 1e-4f)
  }

  @Test
  fun ninetyDegreeRotationSwapsExtents() {
    val rot = RotatedRect(0f, 0f, 20f, 10f, 90f)
    val box = Rect()
    rot.bounds(box)
    assertEquals(10f, box.width, 1e-4f)
    assertEquals(20f, box.height, 1e-4f)
  }

  @Test
  fun fortyFiveDegreeBoundsAreRootTwoTimesLarger() {
    val rot = RotatedRect(0f, 0f, 10f, 10f, 45f)
    val box = Rect()
    rot.bounds(box)
    val expected = 10f * sqrt(2f)
    assertEquals(expected, box.width, 1e-3f)
  }

  @Test
  fun boundsContainAllCorners() {
    val rot = RotatedRect(30f, 40f, 25f, 12f, 33f)
    val box = Rect()
    rot.bounds(box)
    val v = Vec2()
    for (i in 0 until 4) {
      rot.corner(i, v)
      assertTrue(box.grow(1e-3f).contains(v), "角点 " + i + " 应当在外接盒内")
    }
  }

  @Test
  fun cornersFollowRotation() {
    val rot = RotatedRect(0f, 0f, 20f, 10f, 90f)
    val v = Vec2()
    // 未旋转时右下角是 (10, -5)，旋转 90 度后应到 (5, 10)
    rot.corner(3, v)
    assertEquals(5f, v.x, 1e-3f)
    assertEquals(10f, v.y, 1e-3f)
  }

  @Test
  fun localWorldRoundTrip() {
    val rot = RotatedRect(12f, -7f, 8f, 3f, 61f)
    val v = Vec2()
    rot.toWorld(2f, 1f, v)
    assertTrue(rot.contains(v.x, v.y), "本地坐标转世界后应当仍在矩形内")
  }

  @Test
  fun containsRespectsRotation() {
    // 细长矩形旋转 90 度：原本在右侧的点现在不在了
    val rot = RotatedRect(0f, 0f, 40f, 4f, 90f)
    assertTrue(rot.contains(0f, 15f), "旋转后上方应当命中")
    assertFalse(rot.contains(15f, 0f), "旋转后右侧不应命中")
  }

  @Test
  fun overlapsCircleAtCornerGap() {
    // 旋转 45 度的方块，外接盒的四角是空的
    val rot = RotatedRect(0f, 0f, 10f, 10f, 45f)
    val half = 5f * sqrt(2f)
    val cornerX = half - 0.2f
    assertFalse(rot.overlapsCircle(cornerX, cornerX, 0.1f), "外接盒角落不应算作命中")
    assertTrue(rot.overlapsCircle(0f, 0f, 0.1f), "中心应当命中")
  }

  @Test
  fun overlappingRotatedRects() {
    val a = RotatedRect(0f, 0f, 10f, 10f, 0f)
    val b = RotatedRect(8f, 0f, 10f, 10f, 0f)
    assertTrue(a.overlaps(b))

    val far = RotatedRect(50f, 0f, 10f, 10f, 0f)
    assertFalse(a.overlaps(far))
  }

  @Test
  fun separationByRotation() {
    // 两个正方形，中心相距 9，边长 10：轴对齐时相交；一个转 45 度后仍然相交
    val a = RotatedRect(0f, 0f, 10f, 10f, 0f)
    val b = RotatedRect(9f, 0f, 10f, 10f, 45f)
    assertTrue(a.overlaps(b))

    // 拉开到 15 就不相交了
    val c = RotatedRect(15f, 0f, 10f, 10f, 45f)
    assertFalse(a.overlaps(c))
  }

  @Test
  fun overlapsAxisAlignedRect() {
    val rot = RotatedRect(0f, 0f, 30f, 4f, 90f)
    // 旋转后占据 x∈[-2,2], y∈[-15,15]
    assertTrue(rot.overlaps(Rect(-1f, -1f, 2f, 2f)))
    assertFalse(rot.overlaps(Rect(10f, 0f, 2f, 2f)))
  }

  @Test
  fun tileBoundsCoverTheShape() {
    val rot = RotatedRect(20f, 20f, 30f, 8f, 37f)
    val tiles = Seq<Rect>()
    rot.tileBounds(6, tiles)
    assertEquals(6, tiles.size)

    val v = Vec2()
    val full = Rect()
    rot.mergeInto(full)
    for (i in 0 until 4) {
      rot.corner(i, v)
      assertTrue(tiles.any { it.contains(v) || it.grow(0.001f).contains(v) }, "角点 " + i + " 应当被某个分块覆盖")
    }

    // 分块总体积应当小于整体外接盒（说明贴合得更好）
    val tileArea = tiles.sumOf { it.width.toDouble() * it.height }
    val b = Rect()
    rot.bounds(b)
    assertTrue(tileArea < b.width.toDouble() * b.height, "分块面积应小于整体外接盒")
  }

  @Test
  fun mergeIntoGrowsBounds() {
    val box = Rect(0f, 0f, 1f, 1f)
    RotatedRect(50f, 50f, 10f, 10f, 0f).mergeInto(box)
    assertTrue(box.contains(Vec2(45f, 45f)))
    assertTrue(box.contains(Vec2(55f, 55f)))
    assertTrue(box.contains(Vec2(0f, 0f)), "原有范围应当保留")
  }

  @Test
  fun worksAsQuadTree2Object() {
    val tree = QuadTree2<RotatedHitbox>(Rect(0f, 0f, 100f, 100f))
    val a = RotatedHitbox(50f, 50f, 20f, 20f, 45f)
    val b = RotatedHitbox(10f, 10f, 5f, 5f, 0f)
    tree.insert(a)
    tree.insert(b)

    val out = Seq<RotatedHitbox>()
    val query = Rect(40f, 40f, 20f, 20f)
    tree.query(query, QueryMode.Overlap, out)
    assertTrue(out.any { it === a }, "旋转矩形应当被查到")
    assertFalse(out.any { it === b })

    // 精确判定再过滤一遍误报
    val exact = out.filter { it.overlaps(query) }
    assertTrue(exact.any { it === a })
  }
}
