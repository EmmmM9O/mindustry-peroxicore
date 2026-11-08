package peroxicore.struct

import arc.func.*
import arc.math.geom.*
import arc.struct.*

/** 四叉树的查询语义。 */
enum class QueryMode {
  /** 与查询矩形有任何重叠（含包含与被包含）。 */
  Overlap,

  /** 对象被查询矩形完整覆盖。 */
  Contains,

  /** 对象完整覆盖了查询矩形。 */
  ContainedBy,

  /** 与查询矩形相交，但不包含也不被包含。 */
  Crossing,

  /** 只在边界上接触，内部不重叠。 */
  Touching,

  /** 与查询矩形完全一致。 */
  Equals,

  /** 与查询矩形完全不相交。 */
  Disjoint,
}

/**
 * 进阶版四叉树，用于平面上的矩形范围查询。
 *
 * 与 [QuadTree] 的思路类似（每个节点保存范围，放不进某个子象限的对象挂在父节点上），但**实现完全不同**：
 *
 * - 每个对象只在树里保存一份，[remove] 与 [insert] 对称；
 * - 用 [QueryMode] 切换查询语义，而不是给每种语义写一套遍历代码；
 * - 额外提供圆形查询与最近邻查询。
 *
 * ## 查询语义
 *
 * [QueryMode] 决定"什么算命中"，[query] 是最常用的入口：
 *
 * - [QueryMode.Overlap]：与查询矩形**有任何重叠**（包含、被包含、部分相交都算）
 * - [QueryMode.Contains]：对象**被查询矩形完整覆盖**
 * - [QueryMode.ContainedBy]：对象**完整覆盖了**查询矩形
 * - [QueryMode.Crossing]：与查询矩形相交，但**既不包含也不被包含**
 * - [QueryMode.Touching]：只和查询矩形**边界接触**，内部不重叠
 * - [QueryMode.Equals]：与查询矩形**完全相同**
 * - [QueryMode.Disjoint]：与查询矩形**完全不相交**
 *
 * ## 边界规则
 *
 * 一律把矩形当作**闭区间**：边界相接算相交。贴在子象限分界线上的对象会一直挂在父节点上，因此任何查询都不会漏掉它。
 *
 * @param T 对象类型，需要能给出大致包围盒
 */
class QuadTree2<T : QuadTree2.QuadTreeObject>(bounds: Rect) {
  /** 本节点的范围。 */
  val bounds: Rect = bounds

  /** 直接挂在本节点上的对象。 */
  val objects: Seq<T> = Seq(false)

  /** 四个子节点，[split] 之前为 null。 */
  var botLeft: QuadTree2<T>? = null
  var botRight: QuadTree2<T>? = null
  var topLeft: QuadTree2<T>? = null
  var topRight: QuadTree2<T>? = null

  /** 是否没有子节点。 */
  var leaf: Boolean = true

  /** 本子树中的对象总数。 */
  var totalObjects: Int = 0

  /** 保存当前处理对象的包围盒；每个节点独立一份，避免递归时互相覆盖。 */
  private val tmp = Rect()

  /**
   * 把叶子节点一分为四。
   *
   * 能完整落入某个子象限的对象会下放，其余留在本节点。
   */
  protected fun split() {
    if (!leaf) return

    val subW = bounds.width / 2f
    val subH = bounds.height / 2f
    if (subW <= 0f || subH <= 0f) return

    if (botLeft == null) {
      val x = bounds.x
      val y = bounds.y
      botLeft = newChild(Rect(x, y, subW, subH))
      botRight = newChild(Rect(x + subW, y, subW, subH))
      topLeft = newChild(Rect(x, y + subH, subW, subH))
      topRight = newChild(Rect(x + subW, y + subH, subW, subH))
    }
    leaf = false

    var i = 0
    while (i < objects.size) {
      val obj = objects.get(i)
      hitbox(obj)
      val child = getFittingChild(tmp)
      if (child != null) {
        child.objects.add(obj)
        child.totalObjects++
        // 无序删除：用末尾元素补位
        objects.set(i, objects.peek())
        objects.pop()
      } else {
        i++
      }
    }
  }

  /** 把子节点中的对象收回到本节点。 */
  protected fun unsplit() {
    if (leaf) return
    botLeft?.let {
      objects.addAll(it.objects)
      it.clear()
    }
    botRight?.let {
      objects.addAll(it.objects)
      it.clear()
    }
    topLeft?.let {
      objects.addAll(it.objects)
      it.clear()
    }
    topRight?.let {
      objects.addAll(it.objects)
      it.clear()
    }
    leaf = true
  }

  /**
   * 插入一个对象。
   *
   * 包围盒与本节点完全不相交时会被忽略（对象不在树的范围里）。
   */
  open fun insert(obj: T) {
    hitbox(obj)
    if (!bounds.overlaps(tmp) && !bounds.contains(tmp)) return

    totalObjects++

    if (leaf && objects.size + 1 > maxObjectsPerNode) split()

    if (leaf) {
      objects.add(obj)
      return
    }

    hitbox(obj)
    val child = getFittingChild(tmp)
    if (child == null) {
      objects.add(obj)
      return
    }

    // 直接把对象放进能容纳它的子节点，不再调用 insert：否则子节点会把 totalObjects 再自增一次
    child.place(obj)
  }

  /**
   * 把已经计入父节点 [totalObjects] 的对象放进本子树，不再重复计数。
   *
   * 只有对象能完整落入某个子节点时才会继续下放，所以不会出现对象小于节点仍无法下放的情况。
   */
  private fun place(obj: T) {
    if (leaf) {
      objects.add(obj)
      return
    }
    hitbox(obj)
    val child = getFittingChild(tmp)
    if (child == null) {
      objects.add(obj)
    } else {
      child.place(obj)
    }
  }

  /**
   * 移除对象。
   *
   * 插入后修改过包围盒的话需要先移除再重新插入，否则可能找不到。
   *
   * @return 是否真的移除了
   */
  open fun remove(obj: T): Boolean {
    hitbox(obj)
    val child = if (leaf) null else getFittingChild(tmp)
    val result = if (child != null) child.remove(obj) else objects.remove(obj, true)
    if (result) {
      totalObjects--
      collapseIfSparse()
    }
    return result
  }

  /** 清空整棵树。 */
  open fun clear() {
    objects.clear()
    totalObjects = 0
    if (!leaf) {
      botLeft?.clear()
      botRight?.clear()
      topLeft?.clear()
      topRight?.clear()
    }
    leaf = true
  }

  /** 找到能完整容纳 [box] 的子节点；放不进任何一个就返回 null。 */
  protected fun getFittingChild(box: Rect): QuadTree2<T>? {
    val midX = bounds.x + bounds.width / 2f
    val midY = bounds.y + bounds.height / 2f

    val left = if (box.x + box.width < midX) true else if (box.x > midX) false else return null
    val bottom = if (box.y + box.height < midY) true else if (box.y > midY) false else return null

    return when {
      left && bottom -> botLeft
      left && !bottom -> topLeft
      !left && bottom -> botRight
      else -> topRight
    }
  }

  // ---------------------------------------------------------------- 查询

  /** 用给定语义查询，把命中对象加入 [out]。 */
  fun query(rect: Rect, mode: QueryMode = QueryMode.Overlap, out: Seq<T>): Int =
    query(rect.x, rect.y, rect.width, rect.height, mode, out)

  /** 用给定语义查询，命中对象交给 [out] 处理，返回 true 时提前结束。 */
  fun query(rect: Rect, mode: QueryMode = QueryMode.Overlap, out: Boolf<T>): Boolean =
    query(rect.x, rect.y, rect.width, rect.height, mode, out)

  /**
   * 用给定语义查询，把命中对象加入 [out]。
   *
   * 除 [QueryMode.Disjoint] 外都可以用"范围重叠"对节点剪枝。
   *
   * @return [out] 中新增的对象数
   */
  fun query(x: Float, y: Float, width: Float, height: Float, mode: QueryMode = QueryMode.Overlap, out: Seq<T>): Int {
    var added = 0
    val prune = mode != QueryMode.Disjoint
    queryInternal(x, y, width, height, mode, prune, Boolf { item ->
      out.add(item)
      added++
      false
    })
    return added
  }

  /** [query] 的回调版本。 */
  fun query(x: Float, y: Float, width: Float, height: Float, mode: QueryMode = QueryMode.Overlap, out: Boolf<T>): Boolean {
    val prune = mode != QueryMode.Disjoint
    return queryInternal(x, y, width, height, mode, prune, Boolf { item -> out.get(item) })
  }

  /**
   * 内部实现：[consumer] 返回 true 时立即停止。
   *
   * 不能用 inline 递归，所以回调取 [Boolf]；外层用 lambdas 包装时会有一次装箱，
   * 对叶子节点内的对象循环没有影响（那部分在 [queryInternalSeq] 里直接写数组）。
   *
   * @return 是否被提前终止
   */
  private fun queryInternal(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    mode: QueryMode,
    pruneNodes: Boolean,
    consumer: Boolf<T>,
  ): Boolean {
    // 节点剪枝：本节点范围与查询区域无关时整棵子树都不用看
    if (pruneNodes && !bounds.overlaps(x, y, width, height)) return false

    for (i in 0 until objects.size) {
      val item = objects.get(i)
      hitbox(item)
      if (matches(tmp, x, y, width, height, mode) && consumer.get(item)) return true
    }

    if (leaf) return false

    val bl = botLeft!!
    val br = botRight!!
    val tl = topLeft!!
    val tr = topRight!!
    if ((!pruneNodes || bl.bounds.overlaps(x, y, width, height)) &&
      bl.queryInternal(x, y, width, height, mode, pruneNodes, consumer)) return true
    if ((!pruneNodes || br.bounds.overlaps(x, y, width, height)) &&
      br.queryInternal(x, y, width, height, mode, pruneNodes, consumer)) return true
    if ((!pruneNodes || tl.bounds.overlaps(x, y, width, height)) &&
      tl.queryInternal(x, y, width, height, mode, pruneNodes, consumer)) return true
    if ((!pruneNodes || tr.bounds.overlaps(x, y, width, height)) &&
      tr.queryInternal(x, y, width, height, mode, pruneNodes, consumer)) return true
    return false
  }

  /** 是否整个树里存在满足 [mode] 的对象。 */
  fun any(rect: Rect, mode: QueryMode = QueryMode.Overlap): Boolean =
    query(rect.x, rect.y, rect.width, rect.height, mode) { true }

  /** 返回第一个满足 [mode] 的对象，没有时返回 null。 */
  fun find(rect: Rect, mode: QueryMode = QueryMode.Overlap): T? {
    var found: T? = null
    query(rect.x, rect.y, rect.width, rect.height, mode) { item ->
      found = item
      true
    }
    return found
  }

  /** 统计满足 [mode] 的对象数量。 */
  fun count(rect: Rect, mode: QueryMode = QueryMode.Overlap): Int {
    var total = 0
    query(rect.x, rect.y, rect.width, rect.height, mode) {
      total++
      false
    }
    return total
  }

  /** 把所有对象加入 [out]。 */
  fun getObjects(out: Seq<T>) {
    out.addAll(objects)
    if (!leaf) {
      botLeft?.getObjects(out)
      botRight?.getObjects(out)
      topLeft?.getObjects(out)
      topRight?.getObjects(out)
    }
  }

  // ---------------------------------------------------------------- 常用快捷查询

  /** 与 [rect] 有任何重叠的对象。 */
  fun overlap(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.Overlap, out)

  /** 被 [rect] 完整覆盖的对象。 */
  fun coveredBy(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.Contains, out)

  /** 完整覆盖了 [rect] 的对象。 */
  fun covering(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.ContainedBy, out)

  /** 与 [rect] 相交但既不包含也不被包含的对象。 */
  fun crossing(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.Crossing, out)

  /** 只与 [rect] 边界接触的对象。 */
  fun touching(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.Touching, out)

  /** 与 [rect] 完全相同的对象。 */
  fun equalsTo(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.Equals, out)

  /** 与 [rect] 完全不相交的对象。 */
  fun disjoint(rect: Rect, out: Seq<T>): Int = query(rect, QueryMode.Disjoint, out)

  /**
   * 查询与给定圆有交集（相切也算）的对象。
   *
   * 先用外接正方形粗筛，再做一次矩形到圆心的最近点判定。
   */
  fun queryCircle(cx: Float, cy: Float, radius: Float, out: Seq<T>): Int {
    if (radius < 0f) return 0
    val r2 = radius * radius
    var added = 0
    query(cx - radius, cy - radius, radius * 2f, radius * 2f, QueryMode.Overlap, Boolf { item ->
      hitbox(item)
      // 矩形上离圆心最近的点
      val nearX = cx.coerceIn(tmp.x, tmp.x + tmp.width)
      val nearY = cy.coerceIn(tmp.y, tmp.y + tmp.height)
      val dx = cx - nearX
      val dy = cy - nearY
      if (dx * dx + dy * dy <= r2) {
        out.add(item)
        added++
      }
      false
    })
    return added
  }

  /**
   * 找到距离点 ([px], [py]) 最近的 [k] 个对象，按距离从近到远写入 [out]。
   *
   * 距离按对象包围盒的中心计算。树对"最近 k 个"没有可靠的剪枝（对象尺寸未知），
   * 因此这里先取全部对象再部分排序；[k] 很大时请改用 [getObjects] 自行处理。
   */
  fun nearest(px: Float, py: Float, k: Int, out: Seq<Nearest<T>>): Int {
    if (k <= 0) return 0

    val all = Seq<T>()
    getObjects(all)

    val results = Seq<Nearest<T>>()
    for (i in 0 until all.size) {
      val item = all.get(i)
      hitbox(item)
      val centerX = tmp.x + tmp.width / 2f
      val centerY = tmp.y + tmp.height / 2f
      val dx = centerX - px
      val dy = centerY - py
      results.add(Nearest(item, centerX, centerY, dx * dx + dy * dy))
    }
    results.sort(nearestComparator)

    val count = minOf(k, results.size)
    for (i in 0 until count) out.add(results.get(i))
    return count
  }

  // ---------------------------------------------------------------- 几何判定

  /**
   * 判断包围盒 [a] 与查询矩形是否满足 [mode]。
   *
   * 全部按闭区间处理：边界相接算相交。
   */
  protected fun matches(a: Rect, qx: Float, qy: Float, qw: Float, qh: Float, mode: QueryMode): Boolean {
    val ax2 = a.x + a.width
    val ay2 = a.y + a.height
    val qx2 = qx + qw
    val qy2 = qy + qh

    val overlaps = a.x <= qx2 && ax2 >= qx && a.y <= qy2 && ay2 >= qy
    if (mode == QueryMode.Disjoint) return !overlaps
    if (!overlaps) return false

    // 对象被查询矩形完整覆盖
    val contained = a.x >= qx && ax2 <= qx2 && a.y >= qy && ay2 <= qy2
    // 对象完整覆盖了查询矩形
    val covering = a.x <= qx && ax2 >= qx2 && a.y <= qy && ay2 >= qy2

    return when (mode) {
      QueryMode.Overlap -> true
      QueryMode.Contains -> contained
      QueryMode.ContainedBy -> covering
      QueryMode.Crossing -> !contained && !covering
      QueryMode.Equals -> contained && covering
      QueryMode.Touching -> !strictlyOverlaps(a, qx, qy, qw, qh)
      QueryMode.Disjoint -> true
    }
  }

  /** 内部是否真正重叠（有面积交集）；只在边界上接触时返回 false。 */
  protected fun strictlyOverlaps(a: Rect, qx: Float, qy: Float, qw: Float, qh: Float): Boolean =
    a.x < qx + qw && a.x + a.width > qx && a.y < qy + qh && a.y + a.height > qy

  // ---------------------------------------------------------------- 结构维护

  /** 子树变稀疏后把子节点收回来。 */
  private fun collapseIfSparse() {
    if (leaf) return
    botLeft?.collapseIfSparse()
    botRight?.collapseIfSparse()
    topLeft?.collapseIfSparse()
    topRight?.collapseIfSparse()

    var remaining = objects.size
    remaining += botLeft?.objects?.size ?: 0
    remaining += botRight?.objects?.size ?: 0
    remaining += topLeft?.objects?.size ?: 0
    remaining += topRight?.objects?.size ?: 0
    if (remaining <= maxObjectsPerNode) unsplit()
  }

  /** 让对象把自己的大致包围盒写入 [out]。 */
  protected open fun hitbox(t: T) {
    t.hitbox(tmp)
  }

  /** 创建子节点，子类可以重写。 */
  protected open fun newChild(rect: Rect): QuadTree2<T> = QuadTree2(rect)

  /** 表示四叉树中的一个对象。 */
  interface QuadTreeObject {
    /** 把本对象的包围盒写入 [out]；可以偏大，但绝不能偏小。 */
    fun hitbox(out: Rect)
  }

  /** [nearest] 的返回结果。 */
  class Nearest<T>(val obj: T, val x: Float, val y: Float, val dst2: Float)

  companion object {
    /** 每个叶子节点最多容纳的对象数。 */
    const val maxObjectsPerNode: Int = 6

    private val nearestComparator: Comparator<Nearest<*>> = Comparator { a, b -> a.dst2.compareTo(b.dst2) }
  }
}
