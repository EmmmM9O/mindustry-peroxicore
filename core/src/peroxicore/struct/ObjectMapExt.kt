package peroxicore.struct

import arc.struct.*

/*
 * ObjectMap 的 Kotlin 风格扩展。
 *
 * 性能取舍：
 * - 所有带 lambda 的遍历/过滤都声明为 inline，避免为每个调用点生成 Cons2 / Cons / Prov 包装对象；
 * - get/put 只做必要次数的哈希查找，不用 containsKey + get 的二次查找去判空；
 * - 迭代统一走 arc 自带的 entries()/keys()/values()（复用内部迭代器与 Entry 实例），不额外搬一份快照。
 *
 * 注意：arc 的 entries() 在同一 map 上只保证两层嵌套迭代可用（entries1/entries2），
 * 因此这些函数不要在彼此的 lambda 里对同一个 map 做嵌套遍历。
 */

/** 用 [pairs] 建一个 [ObjectMap]，容量按对数一次给足，避免插入过程中扩容。 */
fun <K, V> objectMapOf(vararg pairs: Pair<K, V>): ObjectMap<K, V> {
  val map = ObjectMap<K, V>(pairs.size)
  for (i in pairs.indices) map.put(pairs[i].first, pairs[i].second)
  return map
}

/** 用 builder 语法建一个 [ObjectMap]：`buildObjectMap { put("a", 1) }`。 */
inline fun <K, V> buildObjectMap(builderAction: ObjectMap<K, V>.() -> Unit): ObjectMap<K, V> =
  ObjectMap<K, V>().apply(builderAction)

/** 支持 `for ((key, value) in map.entries())` 的解构。 */
operator fun <K, V> ObjectMap.Entry<K, V>.component1(): K = key

/** 支持 `for ((key, value) in map.entries())` 的解构。 */
operator fun <K, V> ObjectMap.Entry<K, V>.component2(): V = value

/** 把迭代得到的 [ObjectMap.Entry] 转成 [Pair]；Entry 本身是复用的，需要留存时用这个。 */
fun <K, V> ObjectMap.Entry<K, V>.toPair(): Pair<K, V> = key to value

/** 支持 `map[key] = value`（`map[key]` 由 arc 自带的 `get` 直接提供）。 */
operator fun <K, V> ObjectMap<K, V>.set(key: K, value: V) {
  put(key, value)
}

/** 支持 `key in map`，等价于 [ObjectMap.containsKey]。 */
operator fun <K, V> ObjectMap<K, V>.contains(key: K): Boolean = containsKey(key)

/** 不存在（或值为 null）时返回 null；[key] 为 null 时直接返回 null，不会抛异常。 */
fun <K, V> ObjectMap<K, V>.getOrNull(key: K?): V? = if (key == null) null else get(key)

/**
 * 存在则返回已有值，否则返回 [defaultValue] 的结果，**不写入**。
 *
 * 值为 null 的键按缺失处理（与 arc 的 [ObjectMap.get] + supplier 语义一致），这样只做一次哈希查找。
 */
inline fun <K, V> ObjectMap<K, V>.getOrElse(key: K, defaultValue: () -> V): V = get(key) ?: defaultValue()

/**
 * 存在则返回已有值，否则用 [defaultValue] 计算、写入并返回。
 *
 * 缺失时共两次哈希查找（一次 get、一次 put），不会额外分配 `Prov`。
 */
inline fun <K, V> ObjectMap<K, V>.getOrPut(key: K, defaultValue: () -> V): V {
  val value = get(key)
  if (value != null) return value
  val created = defaultValue()
  put(key, created)
  return created
}

/** 键不存在（或值为 null）时写入 [value] 并返回 null；否则保留原值并返回它。 */
fun <K, V> ObjectMap<K, V>.putIfAbsent(key: K, value: V): V? {
  val old = get(key)
  if (old != null) return old
  put(key, value)
  return null
}

/** 写入 [pair] 并返回旧值。 */
fun <K, V> ObjectMap<K, V>.putPair(pair: Pair<K, V>): V? = put(pair.first, pair.second)

/** 支持 `map += key to value`。 */
operator fun <K, V> ObjectMap<K, V>.plusAssign(pair: Pair<K, V>) {
  put(pair.first, pair.second)
}

/** 批量写入 [pairs]。 */
fun <K, V> ObjectMap<K, V>.putPairs(vararg pairs: Pair<K, V>) {
  for (i in pairs.indices) put(pairs[i].first, pairs[i].second)
}

/** 批量写入 [pairs]。 */
fun <K, V> ObjectMap<K, V>.putPairs(pairs: Iterable<Pair<K, V>>) {
  for (pair in pairs) put(pair.first, pair.second)
}

/**
 * 遍历所有键值对，不创建 `Entry` 副本、不分配 lambda 对象。
 *
 * 可以像 Kotlin 的 `forEach` 一样在 lambda 里直接 `return`（inline）。
 */
inline fun <K, V> ObjectMap<K, V>.forEachEntry(action: (K, V) -> Unit) {
  if (size == 0) return
  val entries = entries()
  while (entries.hasNext()) {
    val entry = entries.next()
    action(entry.key, entry.value)
  }
}

/**
 * 返回第一个满足 [predicate] 的键值对，没有时返回 null。
 *
 * 返回的是迭代器内部复用的 `Entry` 实例，只应在当次回调/判断中立即使用；
 * 需要留存请先 [toPair] 或取出 key/value。
 */
inline fun <K, V> ObjectMap<K, V>.findEntry(predicate: (K, V) -> Boolean): ObjectMap.Entry<K, V>? {
  val entries = entries()
  while (entries.hasNext()) {
    val entry = entries.next()
    if (predicate(entry.key, entry.value)) return entry
  }
  return null
}

/** 返回第一个键值对，空 map 返回 null；同样返回复用的 `Entry` 实例。 */
fun <K, V> ObjectMap<K, V>.firstEntryOrNull(): ObjectMap.Entry<K, V>? {
  if (size == 0) return null
  val entries = entries()
  return if (entries.hasNext()) entries.next() else null
}

/**
 * 移除所有满足 [predicate] 的键值对，返回移除的数量。
 *
 * 通过迭代器的 `remove()` 原地删除，不会搬动 stash 里的其它元素，也不会额外分配容器。
 */
inline fun <K, V> ObjectMap<K, V>.removeIf(predicate: (K, V) -> Boolean): Int {
  if (size == 0) return 0
  var removed = 0
  val entries = entries()
  while (entries.hasNext()) {
    val entry = entries.next()
    if (predicate(entry.key, entry.value)) {
      entries.remove()
      removed++
    }
  }
  return removed
}

/** 保留满足 [predicate] 的键值对，原地修改。 */
inline fun <K, V> ObjectMap<K, V>.retainIf(predicate: (K, V) -> Boolean): ObjectMap<K, V> {
  removeIf { key, value -> !predicate(key, value) }
  return this
}

/** 把每个键值对转换成另一个元素，结果放进新 [Seq]（按 [ObjectMap.size] 一次给足容量）。 */
inline fun <K, V, R> ObjectMap<K, V>.mapEntries(transform: (K, V) -> R): Seq<R> {
  val result = Seq<R>(size)
  forEachEntry { key, value -> result.add(transform(key, value)) }
  return result
}

/** 把满足 [predicate] 的键值对放进新 [ObjectMap]，原 map 不变。 */
inline fun <K, V> ObjectMap<K, V>.filterEntries(predicate: (K, V) -> Boolean): ObjectMap<K, V> {
  val result = ObjectMap<K, V>(size)
  forEachEntry { key, value ->
    if (predicate(key, value)) result.put(key, value)
  }
  return result
}

/** 所有键的 [Seq] 快照，容量一次给足。 */
fun <K, V> ObjectMap<K, V>.keysSeq(): Seq<K> = keys().toSeq()

/** 所有值的 [Seq] 快照，容量一次给足。 */
fun <K, V> ObjectMap<K, V>.valuesSeq(): Seq<V> = values().toSeq()
