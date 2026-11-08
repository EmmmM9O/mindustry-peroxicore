package peroxicore.util.pooling

import arc.util.pooling.*

/**
 * 可以被 [IdPool] 管理的载体。
 *
 * [id] 由池维护：[IdPool.put] 时设为分配到的值，[IdPool.free] 时设为 -1。
 * [origin] 是调用方通过 [IdPool.put] 绑定的数据，[reset] 中应当把它清空，避免对象被复用后仍持有旧数据。
 */
interface IdPoolId<D> {
  /** 载体当前持有的 id；-1 表示未被池使用。 */
  var id: Int

  /** 绑定的原始数据，由池在 [IdPool.put] 时写入。 */
  var origin: D?

  /** 归还时调用，用于清空数据；默认什么也不做。 */
  fun reset() {}
}

/**
 * 给对象分配可复用 id 的池，用法上相当于 Arc 的 [Pool] 加一层 id 分配。
 *
 * ```kotlin
 * val pool = IdPool(SomeObject::class.java) { SomeObject() }
 *
 * val a = pool.put(originData)   // 分配 id，并把 originData 写入载体
 * println(a.id)                  // 0
 * pool.free(a)                   // id 0 回到空闲列表
 *
 * val b = pool.put(otherData)
 * println(b.id)                  // 0，id 被复用
 * ```
 *
 * 和 [ValuePool]、[Pool] 一样，**载体本身由池持有**：调用方拿到的是载体，[free] 之后不要再用它，
 * 它可能已经被下一次 [put] 复用并写入别的数据。需要长期保存数据时，请记下 [IdPoolId.id]，
 * 用 [IdPool.get] 反查载体。
 *
 * id 的分配规则：
 * - id 从 0 开始递增，0..[Int.MAX_VALUE] 范围内不会把正在使用中的值分配给别的对象；
 * - [free] 后 id 立即回到空闲列表，下一次 [put] 优先分配**最小**的空闲 id，便于调试时观察；
 * - 载体对象随 id 一起回收，同一个位置下次 [put] 时会复用同一个实例。
 *
 * @param type 载体类型，仅用于 [get] 的语义与调试信息；载体一律由工厂创建
 * @param factory 创建载体的工厂
 * @param initialCapacity 内部数组的初始容量
 */
class IdPool<T : IdPoolId<D>, D>(
  private val type: Class<T>,
  private val factory: () -> T,
  initialCapacity: Int = 4,
) {
  /** 当前仍在使用中的 id 数量。 */
  var size: Int = 0
    private set

  /** 高水位：曾分配过的最大 id + 1，不会因为释放而缩小。 */
  var capacity: Int = 0
    private set

  /** 按 id 存放的载体，保证同一时刻一个 id 只对应一个载体；已释放的载体留在这里等待复用。 */
  private var objects = arrayOfNulls<Any>(initialCapacity.coerceAtLeast(4))

  /** 各 id 当前是否活跃，与 [objects] 一一对应。 */
  private var slotActive = BooleanArray(initialCapacity.coerceAtLeast(4))

  /** 空闲 id（小顶堆），保证下一次 [put] 优先复用最小的 id。 */
  private var freeIds = IntArray(initialCapacity.coerceAtLeast(4))

  private var freeCount = 0

  private var nextId = 0

  init {
    require(initialCapacity >= 0) { "initialCapacity must be >= 0: " + initialCapacity }
  }

  /** 当前可用（已释放、可被复用）的 id 数量。 */
  val free: Int get() = freeCount

  /**
   * 给 [origin] 分配一个 id，返回持有该 id 的载体。
   *
   * @param origin 要绑定的数据
   * @throws IllegalStateException id 用尽（需要 21 亿个同时存活的对象才会发生）
   */
  fun put(origin: D): T {
    val id: Int
    if (freeCount > 0) {
      id = popFree()
    } else {
      check(nextId < Int.MAX_VALUE) { "id exhausted" }
      id = nextId++
    }

    if (id >= objects.size) grow(id + 1)

    val carrier = objects[id] as T? ?: factory().also { objects[id] = it }
    carrier.id = id
    carrier.origin = origin
    slotActive[id] = true

    size++
    if (id >= capacity) capacity = id + 1
    return carrier
  }

  /**
   * 释放 [obj] 持有的 id，载体回到池中等待下次 [put] 复用。
   *
   * 重复释放、或释放不属于本池的对象都会抛 [IllegalArgumentException]。
   */
  fun free(obj: T) {
    val id = obj.id
    // 校验顺序把最常见的越界情况放在前面，短路避免多余判断
    val valid = id >= 0 && id < objects.size && slotActive[id] && objects[id] === obj
    if (!valid) throw IllegalArgumentException("'" + obj + "' is not an active object of this pool")

    // 先把 id 清成 -1，重复 free 会被上面的检查挡住
    obj.id = -1
    obj.reset()

    // 载体保留在槽位里，下次 put 复用同一个实例；"活跃"由 slotActive 区分
    slotActive[id] = false
    pushFree(id)
    size--
  }

  /** 按 id 取回正在使用中的载体；id 不存在或已释放时返回 null。 */
  fun get(id: Int): T? {
    if (id < 0 || id >= capacity || !slotActive[id]) return null
    return objects[id] as T?
  }

  /** 这个 id 当前是否在使用中。 */
  fun isActive(id: Int): Boolean = get(id) != null

  /** 遍历所有正在使用中的载体。 */
  fun each(consumer: (T) -> Unit) {
    for (i in 0 until capacity) {
      if (!slotActive[i]) continue
      consumer(objects[i] as T)
    }
  }

  /** 释放所有 id，并丢弃所有载体。 */
  fun clear() {
    objects.fill(null)
    slotActive.fill(false)
    freeCount = 0
    capacity = 0
    nextId = 0
    size = 0
  }

  private fun grow(minSize: Int) {
    val newSize = maxOf(minSize, objects.size * 2)
    objects = objects.copyOf(newSize)
    slotActive = slotActive.copyOf(newSize)
  }

  // ------------------------------------------------------------------ 空闲 id 小顶堆

  private fun pushFree(id: Int) {
    if (freeCount == freeIds.size) {
      freeIds = freeIds.copyOf(freeCount * 2)
    }
    var i = freeCount++
    freeIds[i] = id
    while (i > 0) {
      val parent = (i - 1) / 2
      if (freeIds[parent] <= freeIds[i]) break
      swap(i, parent)
      i = parent
    }
  }

  private fun popFree(): Int {
    val top = freeIds[0]
    val last = freeIds[--freeCount]
    if (freeCount > 0) {
      freeIds[0] = last
      var i = 0
      while (true) {
        val left = i * 2 + 1
        if (left >= freeCount) break
        val right = left + 1
        val child = if (right < freeCount && freeIds[right] < freeIds[left]) right else left
        if (freeIds[i] <= freeIds[child]) break
        swap(i, child)
        i = child
      }
    }
    return top
  }

  private fun swap(a: Int, b: Int) {
    val t = freeIds[a]
    freeIds[a] = freeIds[b]
    freeIds[b] = t
  }

  override fun toString(): String =
    "IdPool<" + type.simpleName + ">(size=" + size + ", capacity=" + capacity + ", free=" + freeCount + ")"

  companion object {
    /** 用 [type] 的语义创建池；载体一律由 [factory] 创建。 */
    fun <T : IdPoolId<D>, D> get(type: Class<T>, factory: () -> T, initialCapacity: Int = 4): IdPool<T, D> =
      IdPool(type, factory, initialCapacity)
  }
}
