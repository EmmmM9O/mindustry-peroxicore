package peroxicore.util

/**
 * 依赖图中的一个节点。
 *
 * 三组约束共同决定排序：
 * - [dependencies] 与 [after] 中的名字必须排在本节点之前；
 * - [before] 中的名字必须排在本节点之后；
 * - [priority] 只在两个节点之间没有（直接或间接）先后约束时生效，值越大越靠前； 优先级相同的节点按 [name] 排序，保证结果稳定。
 *
 * 同一个排序批次内的 [name] 必须唯一。
 *
 * @property name 节点在图中的唯一标识
 * @property priority 无约束时的排序权重，越大越靠前
 * @property after 必须排在本节点之前的节点名
 * @property before 必须排在本节点之后的节点名
 * @property dependencies 与 [after] 语义相同的依赖名，便于表达“依赖”
 */
abstract class Dependency(
  val name: String,
  val priority: Int = 0,
  val after: Array<String> = emptyArray(),
  val before: Array<String> = emptyArray(),
  val dependencies: Array<String> = emptyArray(),
) {
  /**
   * 返回所有必须排在本节点之前的名字。
   *
   * 子类可以覆写它来提供动态依赖，而无需修改构造参数。
   */
  open fun precedingNames(): Set<String> =
    mutableSetOf<String>().apply {
      addAll(after)
      addAll(dependencies)
    }

  /**
   * 返回所有必须排在本节点之后的 [before] 名字。
   *
   * 子类可以覆写它来提供动态约束，而无需修改构造参数。
   */
  open fun followingNames(): Set<String> = before.toSet()

  override fun toString(): String = "$name($priority)"
}
