@file:Suppress("UNCHECKED_CAST")

package peroxicore.util.handler

import arc.*
import arc.func.*
import arc.struct.*
import universe.util.reflect.*

/**
 * [Events] 的补充工具：精确增删监听器、临时捕获监听器、派发前修改事件。
 *
 * 这里所有操作都直接读写 Arc 内部那张静态的 `events` 表，因此和 [Events.on] / [Events.run]
 * 注册的监听器完全互通（[capture] 期间除外，见该函数的说明）。
 */
object EventsHandler {
  /**
   * Arc 内部的全局监听器表。
   *
   * 原字段是 `private static final`，但反射访问器支持写入（会先去掉 final 修饰），
   * [captureBegin] 正是靠临时替换它来捕获。
   */
  var events: ObjectMap<Any, Seq<Cons<*>>> by Events::class.accessField("events")

  fun removeLastByKey(key: Any): Cons<*>? =
    events.get(key)?.run {
      if (size == 0) return null
      return pop()
    }

  inline fun <reified E> removeLast() = removeLastByKey(E::class.java) as? Cons<E>

  fun <T : Enum<T>> removeLastRun(type: Enum<T>) = removeLastByKey(type) as? Cons<Any>

  // ===== 查询 =====

  /** [key] 下当前的监听器序列；从未注册过时返回 null（不会为它建表）。 */
  fun listenersOf(key: Any): Seq<Cons<*>>? = events.get(key)

  /** 事件类 [E] 下当前的监听器序列；从未注册过时返回 null。 */
  inline fun <reified E> listeners(): Seq<Cons<E>>? = listenersOf(E::class.java) as? Seq<Cons<E>>

  // ===== 注册 =====

  /**
   * 在 [key]（事件类或枚举触发器）下注册 [listener]，并返回它本身，便于之后用 [remove] 精确移除。
   *
   * 与 [Events.on] 等价，只是多了返回值。
   */
  fun add(key: Any, listener: Cons<*>): Cons<*> {
    val seq = events.get(key) ?: Seq<Cons<*>>().also { events.put(key, it) }
    seq.add(listener)
    return listener
  }

  /** 在事件类 [E] 下注册 [listener]，并返回它本身。 */
  inline fun <reified E> add(listener: Cons<E>): Cons<E> = add(E::class.java, listener) as Cons<E>

  /**
   * 用 Kotlin lambda 在事件类 [E] 下注册监听器，返回可直接交给 [remove] 的句柄。
   *
   * ```
   * val handle = EventsHandler.on<WorldLoadEvent> { ... }
   * EventsHandler.remove(handle)
   * ```
   */
  inline fun <reified E> on(noinline listener: (E) -> Unit): Cons<E> {
    val cons = Cons<E> { listener(it) }
    add(E::class.java, cons)
    return cons
  }

  /** 用 Kotlin lambda 注册监听器，key 可以是事件类或枚举触发器。 */
  fun <E> on(key: Any, listener: (E) -> Unit): Cons<E> {
    val cons = Cons<E> { listener(it) }
    add(key, cons)
    return cons
  }

  // ===== 移除 =====

  /**
   * 从 [key] 下移除 [listener]（按引用比较），返回是否真的移除了。
   *
   * 只移除第一个匹配项；通过 [Events.run] 注册的监听器被包了一层 lambda，不能用原来的 [Runnable] 移除。
   */
  fun removeByKey(key: Any, listener: Cons<*>): Boolean =
    events.get(key)?.remove(listener, true) ?: false

  /** 从事件类 [E] 下移除 [listener]。 */
  inline fun <reified E> remove(listener: Cons<E>): Boolean = removeByKey(E::class.java, listener)

  /** 移除 [key] 下的全部监听器，返回移除的数量。 */
  fun removeAllByKey(key: Any): Int = events.remove(key)?.size ?: 0

  /** 移除事件类 [E] 下的全部监听器，返回移除的数量。 */
  inline fun <reified E> removeAll(): Int = removeAllByKey(E::class.java)

  /**
   * 移除 [key] 下满足 [predicate] 的所有监听器，返回移除的数量。
   *
   * 从尾部倒序遍历，避免每次删除都搬移后面的元素。
   */
  inline fun removeIfByKey(key: Any, predicate: (Cons<*>) -> Boolean): Int {
    val seq = events.get(key) ?: return 0
    var removed = 0
    for (i in seq.size - 1 downTo 0) {
      if (predicate(seq.items[i])) {
        seq.remove(i)
        removed++
      }
    }
    return removed
  }

  /** 移除事件类 [E] 下满足 [predicate] 的所有监听器，返回移除的数量。 */
  inline fun <reified E> removeIf(predicate: (Cons<E>) -> Boolean): Int =
    removeIfByKey(E::class.java) { predicate(it as Cons<E>) }

  // ===== 捕获 =====

  /** [captureBegin] 返回的句柄，记录被替换掉的原表和捕获到的监听器。 */
  class Capture internal constructor(
    internal val previous: ObjectMap<Any, Seq<Cons<*>>>,
    /** capture 期间替换掉全局表的副本，期间新注册的监听器都在这里。 */
    val listeners: ObjectMap<Any, Seq<Cons<*>>>,
    /** capture 结束时是否把 [listeners] 里的新监听器合并回原表。 */
    val add: Boolean
  ) {
    /** 各 key 在 capture 开始时的监听器数量，用于合并时只取新增的那一段。 */
    internal val baseSizes = ObjectMap<Any, Int>(previous.size)

    @PublishedApi
    internal var ended = false
  }

  /**
   * 开始捕获：之后的 [Events.on] / [Events.run] 注册会写进一张临时副本，
   * 原有的监听器不受影响（照样会触发）。
   *
   * 每次调用必须用 [captureEnd] 结束，且要按 LIFO 顺序（可以嵌套）。
   *
   * 注意：capture 期间调用的 [Events.remove] 只作用于副本，结束时不会把这种删除同步回原表；
   * 合并只会追加新注册的监听器。
   *
   * 代价：开始时要把整张表和各 key 的监听器序列浅拷贝一份，所以只适合包住一小段注册，
   * 不要放在每帧调用的路径上。
   *
   * @param add 结束时是否把捕获到的监听器追加回原表；false 表示整段注册直接丢弃。
   */
  fun captureBegin(add: Boolean = true): Capture {
    val previous = events
    val copy = ObjectMap<Any, Seq<Cons<*>>>(previous.size)
    val capture = Capture(previous, copy, add)
    val sizes = capture.baseSizes
    val entries = previous.entries()
    while (entries.hasNext()) {
      val entry = entries.next()
      val seq = Seq(entry.value)
      copy.put(entry.key, seq)
      sizes.put(entry.key, seq.size)
    }
    events = copy
    return capture
  }

  /**
   * 结束一次 [captureBegin]，恢复原表。
   *
   * @param merge 是否把捕获到的新监听器合并回原表；默认取 [Capture.add]。
   */
  fun captureEnd(capture: Capture, merge: Boolean = capture.add) {
    check(!capture.ended) { "capture 已经结束过了" }
    check(events === capture.listeners) { "captureEnd 必须按 LIFO 顺序调用" }
    capture.ended = true
    events = capture.previous
    if (merge) mergeInto(capture)
  }

  /**
   * 在捕获模式下执行 [block]（可用 [captureBegin]/[captureEnd] 的简写）。
   *
   * ```
   * EventsHandler.capture {        // 默认 add = true，结束后把期间的监听器并回全局表
   *   Events.on(WorldLoadEvent::class.java) { ... }
   * }
   * ```
   *
   * [block] 抛异常时不会合并，直接丢弃期间的注册并恢复原表；
   * [block] 里就算直接 `return`，原表也会被恢复。
   *
   * @param add 同 [Capture.add]
   * @return [block] 的返回值
   */
  inline fun <R> capture(add: Boolean = true, block: () -> R): R {
    val capture = captureBegin(add)
    var failed = false
    try {
      return block()
    } catch (e: Throwable) {
      failed = true
      throw e
    } finally {
      if (!capture.ended) captureEnd(capture, merge = !failed && capture.add)
    }
  }

  /**
   * 把 capture 期间新增的监听器追加到原表对应 key 的序列末尾。
   *
   * 用 `baseSizes` 跳过开始时拷贝进来的旧监听器，只搬运新增的部分。
   */
  private fun mergeInto(capture: Capture) {
    val added = capture.listeners
    val targetMap = capture.previous
    val sizes = capture.baseSizes
    val entries = added.entries()
    while (entries.hasNext()) {
      val entry = entries.next()
      val base = sizes.get(entry.key, 0)
      val seq = entry.value
      if (seq.size <= base) continue
      val target = targetMap.get(entry.key)
      if (target == null) {
        targetMap.put(entry.key, Seq<Cons<*>>().also { it.addAll(seq.items, base, seq.size - base) })
      } else {
        target.addAll(seq.items, base, seq.size - base)
      }
    }
  }

  // ===== 派发 =====

  /**
   * 直接派发：把 [event] 交给 [key]（事件类或枚举触发器）下的监听器，返回调用的监听器数量。
   *
   * 遍历方式与 [Events.fire] 一致：先记下当前长度，回调期间对同一序列的增删不影响本轮次数。
   */
  fun <T> dispatch(key: Any, event: T): Int {
    val listeners = events.get(key) ?: return 0
    val len = listeners.size
    if (len == 0) return 0
    val items = listeners.items
    for (i in 0 until len) (items[i] as Cons<T>).get(event)
    return len
  }

  /** 按 [event] 推断 key 后派发：枚举用枚举值本身，其它用 [event] 的类（与 [Events.fire] 相同）。 */
  fun <T : Any> dispatch(event: T): Int = dispatch(keyOf(event), event)

  // ===== 拦截 =====

  /** 一次 [process] 安装的拦截器。 */
  class Process internal constructor(
    internal val key: Any,
    /** 安装前的监听器序列，拦截器执行完 block 后委托给它。 */
    internal val delegate: Seq<Cons<*>>,
    /** 写回 `events` 表的序列，第一项就是拦截器本身。 */
    internal val wrapper: Seq<Cons<*>>,
    internal val interceptor: Cons<*>,
    /** 安装时该 key 是否本来就不存在，结束时需要把整条序列去掉。 */
    internal val created: Boolean
  ) {
    /** 拦截器是否仍然生效。 */
    var active = true
      private set

    /**
     * 结束拦截：把拦截器从序列里摘掉，恢复成原来的监听器序列。
     *
     * 安装之后通过 [Events.on] 新注册的监听器会保留，排在原有监听器之后；
     * 这些后注册的监听器直接收到原始事件，不会经过 [process] 的 block。
     *
     * 多次安装必须按 LIFO 顺序结束（与安装顺序相反）。
     */
    fun end() {
      if (!active) return
      check(processes.lastOrNull() === this) { "Process 必须按 LIFO 顺序结束" }
      processes.removeLast()
      active = false

      wrapper.remove(interceptor, true)

      val current = events.get(key)
      if (current === wrapper) {
        if (created && wrapper.size == 0) {
          events.remove(key)
        } else {
          delegate.addAll(wrapper)
          events.put(key, delegate)
        }
      } else {
        // 表被整体换过（capture 或其它替换）：只在当前序列里摘掉拦截器，恢复交给那一层。
        current?.remove(interceptor, true)
      }
    }
  }

  private val processes = ArrayDeque<Process>()

  /**
   * 在 [key] 下安装拦截器：该 key 之后的每次派发都先执行 [block] 修改事件，
   * 再交给安装前的那批监听器。
   *
   * 实现方式是把 `events` 表里 [key] 对应的序列换成只含拦截器的新序列，
   * 拦截器内部再调用原序列，因此 [Events.fire] 会自然经过 [block]。
   *
   * ```
   * val handle = EventsHandler.process<WorldLoadEvent> { it.world = world }
   * handle.end()
   * ```
   *
   * 多次安装会依次串联（后装的先执行 block），结束时用 [Process.end] 按 LIFO 摘除。
   *
   * @return 可用来结束拦截的句柄
   */
  fun <E> process(key: Any, block: (E) -> Unit): Process {
    val existing = events.get(key)
    val delegate = existing ?: Seq<Cons<*>>()
    val interceptor = Cons<E> { event ->
      block(event)
      val len = delegate.size
      val items = delegate.items
      for (i in 0 until len) (items[i] as Cons<E>).get(event)
    }
    val wrapper = Seq<Cons<*>>(1).also { it.add(interceptor) }
    events.put(key, wrapper)

    val handle = Process(key, delegate, wrapper, interceptor, existing == null)
    processes.addLast(handle)
    return handle
  }

  /** 在事件类 [E] 下安装拦截器，见 [process]。 */
  inline fun <reified E> process(noinline block: (E) -> Unit): Process = process(E::class.java, block)

  private fun keyOf(event: Any): Any = if (event is Enum<*>) event else event.javaClass

  fun setup() {}
}
