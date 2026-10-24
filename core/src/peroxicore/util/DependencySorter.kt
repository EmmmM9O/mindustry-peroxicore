package peroxicore.util

/**
 * 依赖排序器抽象。
 *
 * 只规定“把一组 [Dependency] 排成满足约束的顺序”这一契约， 具体使用拓扑排序、纯优先级排序还是其他策略，由实现类决定。
 *
 * 内置实现：[TopologicalDependencySorter]。
 *
 * @param T 节点类型
 */
fun interface DependencySorter<T : Dependency> {
  /**
   * 返回 [nodes] 的排序结果，不修改入参。
   *
   * @throws IllegalArgumentException 节点不合法（例如名字重复）
   */
  fun sort(nodes: Iterable<T>): List<T>

  /**
   * 原地排序 [nodes]，返回同一个列表实例。
   *
   * 排序失败时 [nodes] 不会被修改。
   */
  fun sortInPlace(nodes: MutableList<T>): MutableList<T> {
    val sorted = sort(nodes)
    nodes.clear()
    nodes.addAll(sorted)
    return nodes
  }
}
