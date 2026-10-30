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

fun <T> Seq<T>.getOrPut(id: Int, value: () -> T): T = get(id) ?: value().also { set(id, it) }
