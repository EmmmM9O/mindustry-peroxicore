package peroxicore.util.pooling

import arc.struct.*
import arc.util.pooling.*

/**
 * 按"特征值"分桶的对象池，用法和 Arc 的 [Pool] 一致，但多了一个特征维度。
 *
 * 典型场景是 framebuffer 这类"尺寸取决于屏幕、而屏幕尺寸只有有限几种"的对象：
 *
 * ```kotlin
 * val pool = ValuePool.of { feature: Int -> Framebuffer(sizeOf(feature), sizeOf(feature)) }
 * val screen = pool.hook(0)      // 特征值 0 表示屏幕大小
 * val half = pool.hook(1)        // 特征值 1 表示屏幕大小 / 2，依此类推
 *
 * screen.use { fb -> fb.begin(); ...; fb.end() }
 * ```
 *
 * 特征值取值 [0, [maxFeature]]，共 32 个桶。
 *
 * 几个和 [Pool] 不同、需要留意的点：
 * - 调用方**不需要**、也不应该自己 [free]：对象由 [PoolHook.use] 负责归还。
 *   手工 free 后 [PoolHook.use] 会再 free 一次，导致同一个对象在桶里出现两次。
 * - 不同特征值的对象**永远不会**被互相借用，[obtain] 只从对应桶里取。
 * - [hook] 本身不预分配任何对象，桶是在第一次 [obtain] 时才创建的。
 *
 * ## 自动释放长期不用的对象
 *
 * 可以设置 [idleTicks]，配合外部每轮调用 [nextTick]，让连续 [idleTicks] 轮都没被取用的对象被
 * [dispose]。帧缓冲这类占显存的对象很适合这样做：分辨率切换几帧之后，旧的尺寸就彻底释放了。
 *
 * ```kotlin
 * val pool = ValuePool.of(idleTicks = 60) { f: Int -> Framebuffer(...) }
 *
 * // 每帧调用一次
 * pool.nextTick()
 * ```
 *
 * 释放时机是对象"被归还"之后的第 [idleTicks] 轮，而不是归还的当下；正在被使用的对象永远不会被回收。
 * 池不会自己计时，也不绑定任何游戏循环，完全由 [nextTick] 的调用节奏决定。
 *
 * @param maxFeature 最大特征值（含），最多 32
 * @param factory 按特征值创建新对象的工厂
 * @param initialCapacity 每个桶的初始容量（仅首次创建桶时使用）
 * @param maxPerFeature 每个桶内最多保留的空闲对象数，超出部分会被丢弃
 * @param idleTicks 空闲超过这么多轮后释放对象；<= 0 表示不自动释放
 */
class ValuePool<T : Any>(
  /** 最大特征值（含），取值 0..32。 */
  val maxFeature: Int = MAX_FEATURE,
  private val factory: (Int) -> T,
  private val initialCapacity: Int = 4,
  private val maxPerFeature: Int = Int.MAX_VALUE,
  /** 空闲超过这么多轮后释放；<= 0 表示关闭。 */
  val idleTicks: Int = 0,
) {
  init {
    require(maxFeature in 0..MAX_FEATURE) { "maxFeature must be in 0.." + MAX_FEATURE + ": " + maxFeature }
    require(initialCapacity >= 0) { "initialCapacity must be >= 0: " + initialCapacity }
    require(maxPerFeature > 0) { "maxPerFeature must be > 0: " + maxPerFeature }
  }

  /** 分桶数量，等于 [maxFeature] + 1。 */
  val size: Int get() = maxFeature + 1

  /** 每个桶的空闲对象；桶在第一次使用前是 null，所以 [hook] 不会产生任何分配。 */
  private val buckets = arrayOfNulls<Seq<T>>(size)

  /** 每个桶里各对象最后一次归还时的轮次，与 [buckets] 一一对应；按需创建。 */
  private val idleSince = arrayOfNulls<IntArray>(size)

  /** 按特征值缓存的句柄，避免每次 [hook] 都分配对象。 */
  private val hooks = arrayOfNulls<PoolHook<T>>(size)

  /** 当前轮次，只在 [nextTick] 中推进。 */
  var tick: Int = 0
    private set

  /** 已经通过 [dispose] 释放的对象总数。 */
  var disposed: Int = 0
    private set

  /** 所有桶里空闲对象的总数。 */
  val free: Int
    get() {
      var total = 0
      for (bucket in buckets) total += bucket?.size ?: 0
      return total
    }

  /**
   * 返回指向特征值 [feature] 的句柄。
   *
   * 句柄是轻量的，可缓存复用；同一特征值多次调用 [hook] 得到的行为完全一致。
   *
   * @throws IllegalArgumentException 特征值不在 [0, [maxFeature]] 内
   */
  fun hook(feature: Int): PoolHook<T> {
    checkFeature(feature)
    // 句柄无状态，按特征值缓存，热路径上不产生任何分配
    var hook = hooks[feature]
    if (hook == null) {
      hook = PoolHook(this, feature)
      hooks[feature] = hook
    }
    return hook
  }

  /**
   * 从特征值 [feature] 的桶中取一个对象；桶为空时用 [factory] 新建。
   *
   * 不带 hook，调用方**必须**在 [obtain] 与 [free] 之间成对调用；更推荐使用 [PoolHook.use]。
   */
  fun obtain(feature: Int): T {
    checkFeature(feature)
    val bucket = buckets[feature]
    if (bucket != null && bucket.size > 0) {
      val index = bucket.size - 1
      val obj = bucket.pop()
      // 对应的空闲时间不需要维护：下次归还时会在同一位置写入新的轮次
      idleSince[feature]?.set(index, 0)
      return obj
    }
    // 不能使用 arc.func.Intf：它方法名也叫 get，会和本类的泛型 get 冲突
    return factory(feature)
  }

  /**
   * 把对象还给特征值 [feature] 的桶，并调用 [Pool.Poolable.reset]（如果对象实现了它）。
   *
   * 桶已满时对象会被丢弃（仍然会 reset）。同一个对象可以重复归还而不报错，
   * 但那样会在桶里留下多份引用，因此不要这么做。
   */
  fun free(feature: Int, obj: T) {
    checkFeature(feature)
    if (obj is Pool.Poolable) obj.reset()

    var bucket = buckets[feature]
    if (bucket == null) {
      if (initialCapacity == 0) return
      bucket = Seq(false, initialCapacity)
      buckets[feature] = bucket
    }
    if (bucket.size >= maxPerFeature) return

    bucket.add(obj)
    if (idleTicks > 0) stampsOf(feature).set(bucket.size - 1, tick)
  }

  /**
   * 推进一轮，并根据 [idleTicks] 释放空闲太久的对象。
   *
   * 需要外部每帧（或每个逻辑轮）调用一次；池本身不绑定任何循环。
   *
   * 空闲轮数按"距离上次归还过了多少轮"计算，所以刚归还的对象要等满 [idleTicks] 轮才会被释放。
   *
   * @return 这一轮释放的对象数
   */
  fun nextTick(): Int {
    tick++
    if (idleTicks <= 0) return 0
    return sweep()
  }

  /** 立即释放所有空闲对象，不管它们空闲了多久。 */
  fun sweepAll(): Int {
    var count = 0
    for (feature in 0..maxFeature) {
      val bucket = buckets[feature] ?: continue
      for (i in 0 until bucket.size) {
        dispose(bucket.get(i) as T)
        count++
      }
      buckets[feature] = null
      idleSince[feature] = null
    }
    return count
  }

  /** 清空所有桶；其中的对象按需 [dispose]，然后丢弃所有桶。 */
  fun clear() {
    for (feature in 0..maxFeature) {
      buckets[feature]?.let { bucket ->
        for (i in 0 until bucket.size) dispose(bucket.get(i) as T)
      }
    }
    buckets.fill(null)
    idleSince.fill(null)
  }

  /** 释放空闲时间超过 [idleTicks] 的对象，返回释放数量（不推进轮次）。 */
  private fun sweep(): Int {
    var count = 0
    for (feature in 0..maxFeature) {
      count += sweep(feature)
    }
    return count
  }

  /** 释放 [feature] 桶中空闲轮数 > [idleTicks] 的对象；对象按归还顺序排列，可以提前结束。 */
  private fun sweep(feature: Int): Int {
    val bucket = buckets[feature] ?: return 0
    val stamps = idleSince[feature] ?: return 0

    var count = 0
    var i = 0
    while (i < bucket.size) {
      if (tick - stamps[i] <= idleTicks) break
      dispose(bucket.get(i) as T)
      count++

      // 摘掉这个元素，用末尾元素补位；空闲时间同步补位
      val last = bucket.size - 1
      bucket.set(i, bucket.peek())
      bucket.pop()
      stamps[i] = stamps[last]
    }

    if (bucket.size == 0) {
      buckets[feature] = null
      idleSince[feature] = null
    }
    return count
  }

  /**
   * 真正释放一个不再复用的对象。
   *
   * 实现了 [Disposable] 的对象会收到 [Disposable.dispose]；其他对象只能丢掉引用，等 GC 回收。
   */
  protected open fun dispose(obj: T) {
    if (obj is Disposable) {
      obj.dispose()
      disposed++
    }
  }

  private fun stampsOf(feature: Int): IntArray {
    var stamps = idleSince[feature]
    if (stamps == null) {
      stamps = IntArray(initialCapacity.coerceAtLeast(4))
      idleSince[feature] = stamps
    }
    // 容量要同时覆盖已用大小和底层数组容量：Seq 扩容后容量会大于 size，
    // 后续 free 不会让它再扩容，但我们需要写到那个下标
    val bucket = buckets[feature]
    val needed = maxOf(bucket?.size ?: 0, bucket?.items?.size ?: 0)
    if (needed > stamps.size) {
      val grown = stamps.copyOf(maxOf(needed, stamps.size * 2))
      idleSince[feature] = grown
      return grown
    }
    return stamps
  }

  /** 指定特征值的桶里当前有多少空闲对象。 */
  fun freeOf(feature: Int): Int {
    checkFeature(feature)
    return buckets[feature]?.size ?: 0
  }

  private fun checkFeature(feature: Int) {
    require(feature in 0..maxFeature) {
      "feature must be in 0.." + maxFeature + ": " + feature
    }
  }

  /**
   * 从特征值 [feature] 的桶中取出一个对象交给 [block]，无论 [block] 是否抛异常都会归还。
   *
   * 等价于 `hook(feature).use(block)`，不涉及任何特征值时无需先创建句柄：
   *
   * ```kotlin
   * pool.use(0) { fb -> fb.begin(); ...; fb.end() }
   * ```
   *
   * @return [block] 的返回值
   * @throws IllegalArgumentException 特征值不在 [0, [maxFeature]] 内
   */
  inline fun <R> use(feature: Int, block: (T) -> R): R {
    val obj = obtain(feature)
    try {
      return block(obj)
    } finally {
      free(feature, obj)
    }
  }

  /** 指向某个特征值的池句柄，提供 [use] 以自动归还对象。 */
  class PoolHook<T : Any> @PublishedApi internal constructor(private val pool: ValuePool<T>, val feature: Int) {
    /** 从本特征值的桶中取一个对象。 */
    fun obtain(): T = pool.obtain(feature)

    /** 把对象还给本特征值的桶。 */
    fun free(obj: T) = pool.free(feature, obj)

    /** 本特征值的桶里当前有多少空闲对象。 */
    val free: Int get() = pool.freeOf(feature)

    /**
     * 取出一个对象交给 [block]，无论 [block] 是否抛异常都会归还。
     *
     * @return [block] 的返回值
     */
    inline fun <R> use(block: (T) -> R): R {
      val obj = obtain()
      try {
        return block(obj)
      } finally {
        free(obj)
      }
    }
  }

  /**
   * 池中对象可选实现的回收接口。
   *
   * 与 [Pool.Poolable] 的区别：后者在每次归还时调用，用于清空状态；[dispose] 只在对象被池彻底丢弃时调用一次，
   * 用于释放显存、纹理、native 句柄这类不能靠 GC 回收的资源。
   */
  interface Disposable {
    /** 释放对象持有的资源。调用之后该对象不应再被使用。 */
    fun dispose()
  }

  companion object {
    /** 特征值的上限（含）。 */
    const val MAX_FEATURE: Int = 8

    /**
     * 创建一个特征值范围为 0..[maxFeature] 的池。
     *
     * 之所以不直接写 `ValuePool { ... }`：构造函数还有多个 `Int` 默认参数，
     * 尾随 lambda 会被解析成给 [maxFeature] 传值而不是给工厂。用这个工厂函数可以避免歧义。
     *
     * @param maxFeature 最大特征值（含），取值 0..[MAX_FEATURE]
     * @param initialCapacity 每个桶的初始容量
     * @param maxPerFeature 每个桶内最多保留的空闲对象数
     * @param idleTicks 空闲超过这么多轮后释放对象；<= 0 表示不自动释放
     * @param factory 按特征值创建新对象的工厂
     */
    fun <T : Any> of(
      maxFeature: Int = MAX_FEATURE,
      initialCapacity: Int = 4,
      maxPerFeature: Int = Int.MAX_VALUE,
      idleTicks: Int = 0,
      factory: (Int) -> T,
    ): ValuePool<T> = ValuePool(maxFeature, factory, initialCapacity, maxPerFeature, idleTicks)
  }
}