package peroxicore.struct

import arc.func.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*

/**
 * 基础的八叉树，由 [QuadTree] 改写而来。
 *
 * 本类代表八叉树的任意节点，不过通常情况下你只会和根节点打交道。
 *
 * 每个对象只需要给出一个大致包围盒（[OctreeObject.hitbox]）。包围盒必须完整覆盖对象本身，可以偏大，
 * 但绝不能偏小，否则对象在划分和查询时会被漏掉。在插入和删除之间修改了对象位置的话，
 * 需要先 [remove] 再重新 [insert]。
 *
 * 除了与 [QuadTree] 对应的范围查询外，还提供针对球的射线检测（[raycast] / [raycastFirst] / [pathcast]）。
 *
 * @param T 树中存放的对象类型
 * @author xSke
 * @author Anuke
 */
open class Octree<T : Octree.OctreeObject>(bounds: BoundingBox) {
  /**
   * 保存当前正在处理的对象的包围盒。
   *
   * 查询时不共享静态临时变量：相邻节点会互相剔除对象，递归过程中共享同一个临时变量会写坏状态。
   */
  protected val tmp: BoundingBox = BoundingBox()

  /** 本节点的范围。 */
  val bounds: BoundingBox = bounds

  /**
   * 直接挂在本节点上的对象。
   *
   * 叶子节点中的对象无法完整落入任何子节点；内部节点中的对象跨越了至少一个子节点边界。
   * 使用 unordered 模式，删除时直接把末尾元素搬到原位。
   */
  val objects: Seq<T> = Seq(false)

  /** 八个子节点，[split] 之前都是 null。 */
  var bottomLeftFront: Octree<T>? = null
  var bottomRightFront: Octree<T>? = null
  var topLeftFront: Octree<T>? = null
  var topRightFront: Octree<T>? = null
  var bottomLeftBack: Octree<T>? = null
  var bottomRightBack: Octree<T>? = null
  var topLeftBack: Octree<T>? = null
  var topRightBack: Octree<T>? = null

  /** 是否没有子节点。 */
  var leaf: Boolean = true

  /** 本子树中的对象总数（含本节点 [objects]）。 */
  var totalObjects: Int = 0

  /**
   * 把叶子节点一分为八。
   *
   * 子节点不存在时先创建，然后把能完整放入某个子节点的对象下放。
   *
   * 命名规则：X 轴从左到右（left/right），Y 轴从下到上（bottom/top），Z 轴从前到后（front/back）。
   */
  protected open fun split() {
    if (!leaf) return

    val x = bounds.min.x
    val y = bounds.min.y
    val z = bounds.min.z
    val subW = bounds.getWidth() / 2f
    val subH = bounds.getHeight() / 2f
    val subD = bounds.getDepth() / 2f

    if (bottomLeftFront == null) {
      bottomLeftFront = newChild(BoundingBox(Vec3(x, y, z), Vec3(x + subW, y + subH, z + subD)))
      bottomRightFront = newChild(BoundingBox(Vec3(x + subW, y, z), Vec3(x + subW * 2f, y + subH, z + subD)))
      topLeftFront = newChild(BoundingBox(Vec3(x, y + subH, z), Vec3(x + subW, y + subH * 2f, z + subD)))
      topRightFront = newChild(BoundingBox(Vec3(x + subW, y + subH, z), Vec3(x + subW * 2f, y + subH * 2f, z + subD)))
      bottomLeftBack = newChild(BoundingBox(Vec3(x, y, z + subD), Vec3(x + subW, y + subH, z + subD * 2f)))
      bottomRightBack = newChild(BoundingBox(Vec3(x + subW, y, z + subD), Vec3(x + subW * 2f, y + subH, z + subD * 2f)))
      topLeftBack = newChild(BoundingBox(Vec3(x, y + subH, z + subD), Vec3(x + subW, y + subH * 2f, z + subD * 2f)))
      topRightBack = newChild(BoundingBox(Vec3(x + subW, y + subH, z + subD), Vec3(x + subW * 2f, y + subH * 2f, z + subD * 2f)))
    }
    leaf = false

    // 注意：这里不能调用 insert()。对象恰好与某个子节点边界重合时，分区结果可能不稳定，
    // insert 里"超容量就 split"会被反复触发，导致无限递归。
    // 注意不能直接读写 objects.items：Kotlin 会把 T[] 当作有类型的数组并插入类型检查，
    // 而 Seq 内部实际是 Object[]，会抛 ClassCastException。统一走 Seq 的公开 API。
    var i = 0
    while (i < objects.size) {
      val obj = objects.get(i)
      hitbox(obj)
      val child = getFittingChild(tmp)
      if (child != null) {
        child.objects.add(obj)
        child.totalObjects++

        // 从 objects 中摘下这个元素，并用末尾元素补位，避免搬移整个后半段
        objects.set(i, objects.peek())
        objects.pop()
      } else {
        i++
      }
    }
  }

  /** 把子节点中的所有对象收回到本节点，并丢弃子节点。 */
  protected open fun unsplit() {
    if (leaf) return

    for (i in 0 until 8) {
      val child = childAt(i) ?: continue
      objects.addAll(child.objects)
      child.clear()
    }
    leaf = true
  }

  /**
   * 把对象插入本节点或它的子节点。叶子节点超出容量时会自动 [split]。
   *
   * 对象的包围盒与本节点不相交时会被直接忽略。
   */
  open fun insert(obj: T) {
    hitbox(obj)
    if (!bounds.intersects(tmp)) {
      // 对象不在八叉树范围内，忽略
      return
    }

    totalObjects++

    if (leaf && objects.size + 1 > maxObjectsPerNode) split()

    if (leaf) {
      // 叶子节点没有子节点，直接放在本节点
      objects.add(obj)
    } else {
      hitbox(obj)
      // 能完整放入某个子节点就下放，否则挂在当前节点
      val child = getFittingChild(tmp)
      if (child != null) {
        child.insert(obj)
      } else {
        objects.add(obj)
      }
    }
  }

  /**
   * 从本节点或它的子节点中移除对象。
   *
   * @return 是否真的移除了对象
   */
  open fun remove(obj: T): Boolean {
    var result: Boolean
    if (leaf) {
      // 叶子节点没有子节点，直接从本节点移除
      result = objects.remove(obj, true)
    } else {
      hitbox(obj)
      val child = getFittingChild(tmp)
      result = if (child != null) {
        child.remove(obj)
      } else {
        // 不能完整放入子节点，说明它挂在当前节点
        objects.remove(obj, true)
      }
    }
    if (result) {
      totalObjects--
      collapseIfSparse()
    }
    return result
  }

  /**
   * 对象少到可以收回子节点时把子节点收回来。
   *
   * 删除的目标可能在很深的子节点里，只检查当前节点会漏掉；这里沿路径自底向上统一判断，
   * 条件与 [unsplit] 的语义一致：子树里剩下的对象不超过 [maxObjectsPerNode]。
   */
  private fun collapseIfSparse() {
    if (leaf) return

    // 先把子节点各自的稀疏子树收起来，再判断本节点是否需要收回
    for (i in 0 until 8) {
      childAt(i)?.collapseIfSparse()
    }

    // 子节点收回后，它们剩下来的对象都挂在子节点自己的 objects 里
    var remaining = objects.size
    for (i in 0 until 8) {
      remaining += childAt(i)!!.objects.size
    }

    if (remaining <= maxObjectsPerNode) unsplit()
  }

  /** 移除所有对象，子节点一并清空，并重新变回叶子节点。 */
  open fun clear() {
    objects.clear()
    totalObjects = 0
    if (!leaf) {
      for (i in 0 until 8) {
        childAt(i)?.clear()
      }
    }
    leaf = true
  }

  /** 找到能完整容纳 [boundingBox] 的子节点；放不进任何一个就返回 null。 */
  protected fun getFittingChild(boundingBox: BoundingBox): Octree<T>? {
    val verticalMidpoint = bounds.min.x + bounds.getWidth() / 2f
    val horizontalMidpoint = bounds.min.y + bounds.getHeight() / 2f
    val depthMidpoint = bounds.min.z + bounds.getDepth() / 2f

    // 左右
    val left = boundingBox.max.x < verticalMidpoint
    val right = boundingBox.min.x > verticalMidpoint
    if (!left && !right) return null

    // 上下
    val top = boundingBox.min.y > horizontalMidpoint
    val bottom = boundingBox.max.y < horizontalMidpoint
    if (!top && !bottom) return null

    // 前后
    val front = boundingBox.max.z < depthMidpoint
    val back = boundingBox.min.z > depthMidpoint
    if (!front && !back) return null

    return when {
      left && top && front -> topLeftFront
      left && top && back -> topLeftBack
      left && bottom && front -> bottomLeftFront
      left && bottom && back -> bottomLeftBack
      right && top && front -> topRightFront
      right && top && back -> topRightBack
      right && bottom && front -> bottomRightFront
      right && bottom && back -> bottomRightBack
      else -> null
    }
  }
  // ------------------------------------------------------------------ 范围查询

  /**
   * 处理所有可能和给定包围盒相交的对象。
   *
   * 只按包围盒判断，因此可能有误报，但绝不会漏报。
   */
  open fun intersect(box: BoundingBox, out: Cons<T>) {
    if (!leaf) {
      for (i in 0 until 8) {
        val child = childAt(i)!!
        if (child.bounds.intersects(box)) child.intersect(box, out)
      }
    }

    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (tmp.intersects(box)) out.get(item)
    }
  }

  /**
   * 处理所有可能和给定包围盒相交的对象，[out] 返回 true 时提前结束。
   *
   * 只按包围盒判断，因此可能有误报，但绝不会漏报。
   *
   * @return 是否被 [out] 提前终止
   */
  open fun intersect(box: BoundingBox, out: Boolf<T>): Boolean {
    if (!leaf) {
      for (i in 0 until 8) {
        val child = childAt(i)!!
        if (child.bounds.intersects(box) && child.intersect(box, out)) return true
      }
    }

    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (tmp.intersects(box) && out.get(item)) return true
    }
    return false
  }

  /**
   * 在树中寻找任意一个可能和给定包围盒相交、且满足 [out] 的对象。
   *
   * 只按包围盒判断，因此可能有误报，但绝不会漏报。
   */
  open fun find(box: BoundingBox, out: Boolf<T>): T? {
    if (!leaf) {
      for (i in 0 until 8) {
        val child = childAt(i)!!
        if (child.bounds.intersects(box)) {
          val result = child.find(box, out)
          if (result != null) return result
        }
      }
    }

    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (tmp.intersects(box) && out.get(item)) return item
    }
    return null
  }

  /**
   * @return 是否有任何对象可能和给定包围盒相交（只按包围盒判断，可能有误报，但绝不会漏报）
   */
  open fun any(box: BoundingBox): Boolean {
    if (!leaf) {
      for (i in 0 until 8) {
        val child = childAt(i)!!
        if (child.bounds.intersects(box) && child.any(box)) return true
      }
    }

    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (tmp.intersects(box)) return true
    }
    return false
  }

  /**
   * 把可能和给定包围盒相交的对象收集到 [out]。
   *
   * 只按包围盒判断，因此可能有误报，但绝不会漏报。
   */
  open fun intersect(box: BoundingBox, out: Seq<T>) {
    if (!leaf) {
      for (i in 0 until 8) {
        val child = childAt(i)!!
        if (child.bounds.intersects(box)) child.intersect(box, out)
      }
    }

    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (tmp.intersects(box)) out.add(item)
    }
  }

  /** 把整棵子树中的对象全部加入 [out]。 */
  open fun getObjects(out: Seq<T>) {
    out.addAll(objects)

    if (!leaf) {
      for (i in 0 until 8) {
        childAt(i)?.getObjects(out)
      }
    }
  }

  // ------------------------------------------------------------------ 射线检测

  /**
   * 射线与球的碰撞检测。
   *
   * 先用射线和节点的包围盒做剔除，再对候选对象用 [Intersector3D.intersectRaySphere] 做精确判定。
   * 结果按命中距离**从近到远**排序，[RayHit.dst] 是射线原点到球面的距离。
   *
   * 球心取对象包围盒的中心（见 [OctreeObject.hitbox]），半径由 [radiusOf] 给出。
   * 射线方向必须是单位向量。
   *
   * @param ray 射线本身，不会被修改
   * @param length 射线长度，超过这个距离的命中会被忽略
   * @param radiusOf 返回对象的球半径；半径 <= 0 的对象会被跳过
   * @param out 接收命中；由调用方负责清空
   */
  open fun raycast(ray: Ray, length: Float, radiusOf: Floatf<T>, out: Seq<RayHit<T>>) {
    if (length < 0f) return

    shadow(ray, length, radiusOf, out, 0)

    // 全局排序：只有跨越多个子节点时才需要
    if (!leaf) out.sort(byDst)
  }

  /**
   * 射线检测的递归实现。
   *
   * 候选数组按"层"分段：第 [depth] 层固定使用 [depth * 8, depth * 8 + 8) 这一段。
   * 每层最多 8 个子节点，段与段之间不会重叠，递归也不需要复制。
   *
   * @param depth 当前节点在树中的深度，也就是它占用第几段
   */
  private fun shadow(
    ray: Ray,
    length: Float,
    radiusOf: Floatf<T>,
    out: Seq<RayHit<T>>,
    depth: Int,
  ) {
    // 本节点的对象：顺序遍历，命中直接追加
    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)

      // 粗略剔除：包围盒都不相交就不可能是候选
      if (!segmentIntersectsBox(ray, length, tmp)) continue

      val radius = radiusOf.get(item)
      if (radius <= 0f) continue

      val cx = tmp.getCenterX()
      val cy = tmp.getCenterY()
      val cz = tmp.getCenterZ()

      // 一次求交同时得到判定与距离，省掉重复调用 Intersector3D 的开销
      val dst = hitDstAt(ray, length, cx, cy, cz, radius)
      if (dst >= 0f) out.add(RayHit(item, cx, cy, cz, radius, dst))
    }

    if (leaf) return

    // 收集被射线穿过的子节点及进入距离，写在本层对应的段里
    val base = depth * 8
    ensureShadowDepth(depth + 1)
    val cands = shadowNodes
    val entries = shadowEntries
    var count = 0
    for (i in 0 until 8) {
      val child = childAt(i) ?: continue
      val entry = slabEntry(ray, length, child.bounds)
      if (entry.isNaN()) continue
      cands[base + count] = child
      entries[base + count] = entry
      count++
    }

    // 插入排序：候选 <= 8 个，比通用排序器快，且完全不触碰 out
    for (i in 1 until count) {
      val currentNode = cands[base + i]
      val currentEntry = entries[base + i]
      var j = i
      while (j > 0 && entries[base + j - 1] > currentEntry) {
        cands[base + j] = cands[base + j - 1]
        entries[base + j] = entries[base + j - 1]
        j--
      }
      cands[base + j] = currentNode
      entries[base + j] = currentEntry
    }

    for (i in 0 until count) {
      // 已命中时，比本次进入距离更远的子节点不可能产生更近的命中
      if (out.size > 0 && entries[base + i] > nearestDst(out)) break
      val child = cands[base + i] as Octree<T>
      child.shadow(ray, length, radiusOf, out, depth + 1)
    }

    // 本段用完就立即清空，避免长时间持有子节点引用
    for (i in 0 until count) cands[base + i] = null
  }

  /** 当前收集到的最近命中距离；列表为空时返回正无穷。 */
  private fun nearestDst(hits: Seq<RayHit<T>>): Float {
    var best = Float.POSITIVE_INFINITY
    for (i in 0 until hits.size) {
      val dst = hits.get(i).dst
      if (dst < best) best = dst
    }
    return best
  }

  /**
   * 射线与球的碰撞检测，只返回距离射线原点最近的一个命中；没有命中时返回 null。
   *
   * 参数含义与 [raycast] 相同。与 [raycast] 不同，这里**不分配**任何 [RayHit]，也不收集全部命中，
   * 只维护当前最近的一个，适合"点到谁"这类高频查询。
   */
  open fun raycastFirst(ray: Ray, length: Float, radiusOf: Floatf<T>): RayHit<T>? {
    if (length < 0f) return null
    // 查询状态随调用向下传递，所有节点共享同一份
    @Suppress("UNCHECKED_CAST")
    val state = shadowState as RaycastState<T>
    state.reset()
    shadowFirst(ray, length, radiusOf, state, 0)
    val best = state.best
    state.best = null
    return best
  }

  /**
   * [raycastFirst] 的递归实现。
   *
   * 最近命中保存在 [state] 里，由根节点创建、所有子节点共享，因此是整次查询的全局最优。
   */
  private fun shadowFirst(ray: Ray, length: Float, radiusOf: Floatf<T>, state: RaycastState<T>, depth: Int) {
    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (!segmentIntersectsBox(ray, length, tmp)) continue

      val radius = radiusOf.get(item)
      if (radius <= 0f) continue

      val cx = tmp.getCenterX()
      val cy = tmp.getCenterY()
      val cz = tmp.getCenterZ()
      val dst = hitDstAt(ray, length, cx, cy, cz, radius)
      if (dst >= 0f && dst < state.bestDst) {
        state.bestDst = dst
        val best = state.best
        if (best == null) {
          state.best = RayHit(item, cx, cy, cz, radius, dst)
        } else {
          best.set(item, cx, cy, cz, radius, dst)
        }
      }
    }

    if (leaf) return

    val base = depth * 8
    ensureShadowDepth(depth + 1)
    val cands = shadowNodes
    val entries = shadowEntries
    var count = 0
    for (i in 0 until 8) {
      val child = childAt(i) ?: continue
      val entry = slabEntry(ray, length, child.bounds)
      if (entry.isNaN()) continue
      cands[base + count] = child
      entries[base + count] = entry
      count++
    }
    for (i in 1 until count) {
      val currentNode = cands[base + i]
      val currentEntry = entries[base + i]
      var j = i
      while (j > 0 && entries[base + j - 1] > currentEntry) {
        cands[base + j] = cands[base + j - 1]
        entries[base + j] = entries[base + j - 1]
        j--
      }
      cands[base + j] = currentNode
      entries[base + j] = currentEntry
    }

    for (i in 0 until count) {
      if (entries[base + i] > state.bestDst) break
      // 候选数组是共享的 object 数组，取回时要还原成带泛型的节点
      val child = cands[base + i] as Octree<T>
      child.shadowFirst(ray, length, radiusOf, state, depth + 1)
    }

    for (i in 0 until count) cands[base + i] = null
  }

  /**
   * 射线与单个球求交，返回相对射线原点的最近命中；没有命中时返回 null。
   *
   * 返回的 [RayHit.obj] 为 null（这里没有对象上下文）。
   * 射线原点在球内时 [RayHit.dst] 为 0。
   */
  open fun pathcast(ray: Ray, length: Float, center: Vec3, radius: Float): RayHit<T>? {
    if (radius <= 0f || length < 0f) return null
    val dst = hitDst(ray, length, center.x, center.y, center.z, radius) ?: return null
    return RayHit(null, center.x, center.y, center.z, radius, dst)
  }

  /** 创建子节点，子类可以重写以返回自己的实例。 */
  protected open fun newChild(rect: BoundingBox): Octree<T> = Octree(rect)

  /** 让对象把大致包围盒写入 [out]。 */
  protected open fun hitbox(t: T) {
    t.hitbox(tmp)
  }

  /** 按索引取子节点，顺序与 [split] 中的定义一致。 */
  protected fun childAt(index: Int): Octree<T>? = when (index) {
    0 -> bottomLeftFront
    1 -> bottomRightFront
    2 -> topLeftFront
    3 -> topRightFront
    4 -> bottomLeftBack
    5 -> bottomRightBack
    6 -> topLeftBack
    7 -> topRightBack
    else -> null
  }

  // ------------------------------------------------------------------ 几何工具

  /**
   * 射线是否在 [length] 范围内与包围盒相交（slab 法）。
   *
   * 射线原点在包围盒内部时同样返回 true。
   */
  protected fun segmentIntersectsBox(ray: Ray, length: Float, box: BoundingBox): Boolean =
    !slabEntry(ray, length, box).isNaN()


  /**
   * 射线原点到球面的距离；不相交、整个球在射线背后或超出 [length] 时返回 -1。
   *
   * 用负数而不是装箱的 [Float] 表示"未命中"，热路径上不产生任何分配。
   * 射线原点在球内时返回 0。
   */
  protected fun hitDstAt(ray: Ray, length: Float, cx: Float, cy: Float, cz: Float, radius: Float): Float {
    val ox = ray.origin.x - cx
    val oy = ray.origin.y - cy
    val oz = ray.origin.z - cz
    val b = ox * ray.direction.x + oy * ray.direction.y + oz * ray.direction.z
    val c = ox * ox + oy * oy + oz * oz - radius * radius
    val disc = b * b - c
    if (disc < 0f) return -1f

    val sqrt = Mathf.sqrt(disc)
    // 整个球都在射线原点背后
    if (-b + sqrt < 0f) return -1f

    val near = -b - sqrt
    val dst = if (near < 0f) 0f else near
    return if (dst > length) -1f else dst
  }

  /** 兼容旧接口：未命中返回 null。内部热路径请直接用 [hitDstAt]。 */
  protected fun hitDst(ray: Ray, length: Float, cx: Float, cy: Float, cz: Float, radius: Float): Float? {
    val dst = hitDstAt(ray, length, cx, cy, cz, radius)
    return if (dst < 0f) null else dst
  }

  /**
   * 射线进入包围盒的距离；未相交返回 [Float.NaN]。
   *
   * 与 [segmentIntersectsBox] 合并：一次计算同时给出"是否相交"和"进入距离"。
   */
  protected fun slabEntry(ray: Ray, length: Float, box: BoundingBox): Float {
    val o = ray.origin
    val d = ray.direction
    var tmin = 0f
    var tmax = length

    if (d.x == 0f) {
      if (o.x < box.min.x || o.x > box.max.x) return Float.NaN
    } else {
      val inv = 1f / d.x
      var t1 = (box.min.x - o.x) * inv
      var t2 = (box.max.x - o.x) * inv
      if (t1 > t2) {
        val t = t1
        t1 = t2
        t2 = t
      }
      if (t1 > tmin) tmin = t1
      if (t2 < tmax) tmax = t2
    }

    if (d.y == 0f) {
      if (o.y < box.min.y || o.y > box.max.y) return Float.NaN
    } else {
      val inv = 1f / d.y
      var t1 = (box.min.y - o.y) * inv
      var t2 = (box.max.y - o.y) * inv
      if (t1 > t2) {
        val t = t1
        t1 = t2
        t2 = t
      }
      if (t1 > tmin) tmin = t1
      if (t2 < tmax) tmax = t2
    }

    if (d.z == 0f) {
      if (o.z < box.min.z || o.z > box.max.z) return Float.NaN
    } else {
      val inv = 1f / d.z
      var t1 = (box.min.z - o.z) * inv
      var t2 = (box.max.z - o.z) * inv
      if (t1 > t2) {
        val t = t1
        t1 = t2
        t2 = t
      }
      if (t1 > tmin) tmin = t1
      if (t2 < tmax) tmax = t2
    }

    return if (tmin <= tmax) tmin else Float.NaN
  }

  /** 射线进入包围盒的距离；原点在内部或平行轴时取 0。未相交时返回 0。 */
  protected fun entryDst(ray: Ray, box: BoundingBox): Float {
    val entry = slabEntry(ray, Float.POSITIVE_INFINITY, box)
    return if (entry.isNaN()) 0f else entry
  }

  // ------------------------------------------------------------------ 内部池

  /** 取出子节点排序用的临时数组；其中的元素具体类型由调用方决定，这里不做类型转换。 */
  private fun pooledChildren(): Array<Any?> = childPool

  /** 表示八叉树中的一个对象。 */
  interface OctreeObject {
    /**
     * 把本对象的大致包围盒写入 [out]。
     *
     * 可以比实际形状大，但绝不能比实际形状小，否则对象会在划分或查询时被漏掉。
     */
    fun hitbox(out: BoundingBox)
  }

  /** 一次射线命中。 */
  class RayHit<T>(
    /** 被命中的对象；[pathcast] 直接对球体求交时为 null。 */
    var obj: T?,
    /** 命中球的球心。 */
    var x: Float,
    var y: Float,
    var z: Float,
    /** 命中球的半径。 */
    var radius: Float,
    /** 射线原点（或线段起点）到球面的距离；原点在球内时为 0。 */
    var dst: Float,
  ) {
    /** 原地改写为另一次命中，供 [raycastFirst] 复用实例。 */
    internal fun set(obj: T?, x: Float, y: Float, z: Float, radius: Float, dst: Float) {
      this.obj = obj
      this.x = x
      this.y = y
      this.z = z
      this.radius = radius
      this.dst = dst
    }

    /** 把命中点写入 [out]。 */
    fun point(ray: Ray, out: Vec3): Vec3 = ray.getEndPoint(out, dst)

    override fun toString(): String = "RayHit(obj=" + obj + ", dst=" + dst + ")"
  }

  /** [raycastFirst] 的查询状态：当前最近命中及其距离。 */
  class RaycastState<T> {
    var best: RayHit<T>? = null
    var bestDst: Float = Float.POSITIVE_INFINITY

    fun reset() {
      best = null
      bestDst = Float.POSITIVE_INFINITY
    }
  }

  companion object {
    /** 每个叶子节点最多容纳的对象数。 */
    const val maxObjectsPerNode: Int = 5

    /** 按命中距离升序比较。 */
    val byDst: Comparator<RayHit<*>> = Comparator { a, b -> a.dst.compareTo(b.dst) }

    /**
     * 射线检测共享的候选数组，按深度分段：第 d 层占用 [d * 8, d * 8 + 8)。
     *
     * 每层最多 8 个子节点，段与段互不重叠，所以递归无需复制；代价是不能并发调用。
     */
    private var shadowNodes: Array<Any?> = arrayOfNulls(8)

    private var shadowEntries: FloatArray = FloatArray(8)

    /** [raycastFirst] 的查询状态；共享候选数组已意味着不能并发调用，这里保持一致。 */
    private val shadowState: RaycastState<Any> = RaycastState()

    /** 保证共享数组至少有 [depth] 层；只在需要时才扩容。 */
    private fun ensureShadowDepth(depth: Int) {
      val needed = depth * 8
      if (needed <= shadowEntries.size) return
      var newSize = shadowEntries.size
      while (newSize < needed) newSize *= 2
      shadowNodes = shadowNodes.copyOf(newSize)
      shadowEntries = shadowEntries.copyOf(newSize)
    }

    /** [raycast] 中给子节点排序用的临时数组，最多 8 个元素。 */
    private val childPool: Array<Any?> = arrayOfNulls(8)

    /** 球体求交用的临时球心。 */
    private val tmpCenter: Vec3 = Vec3()
  }
}