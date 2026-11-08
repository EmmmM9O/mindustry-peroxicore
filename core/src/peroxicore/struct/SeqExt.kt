package peroxicore.struct

import arc.struct.*

/**
 * 如果 [Seq.size] > [id]，直接把 [value] 放到 [id] 位置； 否则先用 null 扩容数组，直到 size > id，然后再 put。
 *
 * 中间缺失的位置会填充 null。
 *
 * @param id 目标下标，必须 >= 0
 * @param value 要放入的值
 * @return 当前 Seq，便于链式调用
 */
fun <T> Seq<T>.putOrGrow(id: Int, value: T): Seq<T> = apply {
  require(id >= 0) { "id must be >= 0: $id" }

  if (size > id) {
    set(id, value)
  } else {
    ensureCapacity(id + 1 - size)
    while (size < id) add(null as T)
    add(value)
  }
}

/**
 * 如果 [Seq.size] > [id]，返回 [id] 位置的元素； 否则先用 null 扩容数组直到 size > id，再用 [value] 填入该位置并返回。
 *
 * 中间缺失的位置会填充 null。
 *
 * @param id 目标下标，必须 >= 0
 * @param value 当位置不存在时用于填充的值
 * @return [id] 位置的元素
 */
fun <T> Seq<T>.getOrPutGrow(id: Int, value: () -> T): T = apply {
  require(id >= 0) { "id must be >= 0: $id" }

  if (size > id) return getOrPut(id, value)

  ensureCapacity(id + 1 - size)
  while (size < id) add(null as T)
  add(value())
}
  .get(id)

/**
 * 取 [id] 位置的元素；该位置不存在或是 null 时，用 [value] 计算并写入后返回。
 *
 * 下标越界时会先扩容并在中间填充 null，因此不会抛 [IndexOutOfBoundsException]。
 *
 * @param id 目标下标，必须 >= 0
 * @return [id] 位置的元素
 */
fun <T> Seq<T>.getOrPut(id: Int, value: () -> T): T {
  require(id >= 0) { "id must be >= 0: $id" }
  if (id < size) return get(id) ?: value().also { set(id, it) }

  ensureCapacity(id + 1 - size)
  while (size < id) add(null as T)
  val created = value()
  add(created)
  return created
}

/**
 * 返回 [index] 处的元素，下标越界时返回 null。
 *
 * 与 [Seq.get] 不同，这里不会抛 [IndexOutOfBoundsException]；配合 [getOrNull] 可以直接做安全的链式访问。
 */
fun <T> Seq<T>.getOrNull(index: Int): T? = if (index in 0 until size) get(index) else null

/** 返回第一个元素，[Seq] 为空时返回 null。 */
fun <T> Seq<T>.firstOrNull(): T? = if (size == 0) null else get(0)

/** 返回最后一个元素，[Seq] 为空时返回 null。 */
fun <T> Seq<T>.lastOrNull(): T? = if (size == 0) null else get(size - 1)

/** [Seq] 是否为空。 */
val <T> Seq<T>.isEmpty: Boolean get() = size == 0

/** [Seq] 是否非空。 */
val <T> Seq<T>.isNotEmpty: Boolean get() = size > 0

/**
 * [Seq] 的最后一个可用下标；为空时返回 -1。
 *
 * 适合写 `for (i in seq.indices)` 这种下标循环。
 */
val <T> Seq<T>.lastIndex: Int get() = size - 1

/**
 * 移除所有满足 [predicate] 的元素，返回移除的数量。
 *
 * 原地修改；元素按原有顺序保留（会搬移后面的元素）。遍历时注意不要在 [predicate] 里修改 [Seq]。
 *
 * @return 移除的元素个数
 */
fun <T> Seq<T>.removeIf(predicate: (T) -> Boolean): Int {
  var removed = 0
  var i = 0
  while (i < size) {
    if (predicate(get(i))) {
      remove(i)
      removed++
    } else {
      i++
    }
  }
  return removed
}

/**
 * 按 [key] 分组，返回 key 到元素列表的映射，保持原有的相对顺序。
 *
 * 与 Kotlin 的 [groupBy] 行为一致，只是容器换成 [Seq]。
 */
fun <T, K> Seq<T>.groupBySeq(key: (T) -> K): ObjectMap<K, Seq<T>> {
  val result = ObjectMap<K, Seq<T>>()
  for (i in 0 until size) {
    val value = get(i)
    val group = result.get(key(value))
    if (group == null) {
      result.put(key(value), Seq<T>().apply { add(value) })
    } else {
      group.add(value)
    }
  }
  return result
}

/**
 * 把每个元素转换成另一个元素，返回新 [Seq]，原 [Seq] 不会被修改。
 *
 * 与 Kotlin 的 [map] 行为一致，只是结果容器换成 [Seq]。
 */
inline fun <T, R> Seq<T>.mapSeq(transform: (T) -> R): Seq<R> {
  val result = Seq<R>(size)
  for (i in 0 until size) result.add(transform(get(i)))
  return result
}

/**
 * 保留满足 [predicate] 的元素，返回新 [Seq]，原 [Seq] 不会被修改。
 *
 * 与 [Seq.select] 的区别：这里返回新 [Seq]，不修改原对象。
 */
inline fun <T> Seq<T>.filterSeq(predicate: (T) -> Boolean): Seq<T> {
  val result = Seq<T>(size)
  for (i in 0 until size) {
    val value = get(i)
    if (predicate(value)) result.add(value)
  }
  return result
}

/**
 * 返回第一个满足 [predicate] 的元素，没有时返回 null。
 *
 * 与 [Seq.find] 的区别：语义与 Kotlin 的 [firstOrNull] 一致。之所以不叫 `firstOrNull`，
 * 是为了避免和无参的 [firstOrNull] 扩展在带谓词调用时产生歧义。
 */
inline fun <T> Seq<T>.findFirst(predicate: (T) -> Boolean): T? {
  for (i in 0 until this.size) {
    val value = this.get(i)
    if (predicate(value)) return value
  }
  return null
}

/**
 * 从尾部查找最后一个满足 [predicate] 的元素，没有时返回 null。
 */
inline fun <T> Seq<T>.findLast(predicate: (T) -> Boolean): T? {
  for (i in this.size - 1 downTo 0) {
    val value = this.get(i)
    if (predicate(value)) return value
  }
  return null
}

/**
 * 是否没有任何元素满足 [predicate]；空 [Seq] 返回 true。
 *
 * 正向判断请直接用 [Seq.allMatch]，这里只补充取反的常见写法。
 */
inline fun <T> Seq<T>.noneMatch(predicate: (T) -> Boolean): Boolean {
  for (i in 0 until size) if (predicate(get(i))) return false
  return true
}

/**
 * 把 [Seq] 中的每个非 null 元素加入 [out]；[out] 可以就是当前 [Seq] 之外的新容器。
 *
 * 对可空元素的 [Seq] 使用，返回值仍是可空类型，便于继续链式调用。
 */
fun <T : Any> Seq<T?>.filterNotNull(): Seq<T> {
  val result = Seq<T>(size)
  for (i in 0 until size) {
    val value = get(i)
    if (value != null) result.add(value)
  }
  return result
}

/**
 * 用 [value] 替换所有 null 元素，返回新 [Seq]。
 */
fun <T> Seq<T?>.replaceNulls(value: T): Seq<T> {
  val result = Seq<T>(size)
  for (i in 0 until size) {
    val current = get(i)
    result.add(current ?: value)
  }
  return result
}

/**
 * 把 [Seq] 中的元素按 [key] 建立下标映射，便于后续 [get] 查找。
 *
 * 键重复时保留**第一个**出现的元素。
 */
fun <T, K> Seq<T>.associateBySeq(key: (T) -> K): ObjectMap<K, T> {
  val result = ObjectMap<K, T>(size)
  for (i in 0 until size) {
    val value = get(i)
    val k = key(value)
    if (!result.containsKey(k)) result.put(k, value)
  }
  return result
}

/**
 * 反转 [Seq]，原地修改。
 *
 * @return 当前 [Seq]，便于链式调用
 */
fun <T> Seq<T>.reverseInPlace(): Seq<T> = apply {
  var i = 0
  var j = size - 1
  while (i < j) {
    swap(i++, j--)
  }
}

/**
 * 把 [Seq] 中满足 [predicate] 的前 [count] 个元素加入 [out]。
 *
 * @param count 最多取多少个，必须 >= 0
 * @param out 收集结果，调用方负责清空
 * @return 实际加入的数量
 */
fun <T> Seq<T>.takeWhile(count: Int, predicate: (T) -> Boolean, out: Seq<T>): Int {
  require(count >= 0) { "count must be >= 0: $count" }
  var taken = 0
  for (i in 0 until size) {
    if (taken >= count) break
    val value = get(i)
    if (!predicate(value)) break
    out.add(value)
    taken++
  }
  return taken
}

/** [Seq] 中追加一个元素并返回它，避免 `add(x); x` 这种写法。 */
fun <T> Seq<T>.addAndGet(value: T): T {
  add(value)
  return value
}

/**
 * 把 [Seq] 转成 Kotlin 的 [List]；[Seq] 后续的修改不会反映到返回的列表上。
 */
fun <T> Seq<T>.toListCopy(): List<T> = buildList(this@toListCopy.size) {
  for (i in 0 until this@toListCopy.size) add(this@toListCopy.get(i))
}