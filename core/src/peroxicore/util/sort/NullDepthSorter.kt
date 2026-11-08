package peroxicore.util.sort

import arc.struct.*

/**
 * 不做任何排序的排序器。
 *
 * 用于关闭排序，或者在对象本身已经有序时避免多余开销；也可以作为基准测试的对照组。
 */
class NullDepthSorter<T : DepthItem> : DepthSorter<T> {
  override fun <S : Seq<T>> sort(items: S): S = items

  override fun forEachDraw(items: Seq<T>, consumer: (T) -> Unit) {
    for (i in 0 until items.size) consumer(items.get(i))
  }
}
