package peroxicore.util

import java.util.*

/**
 * 基于拓扑排序的 [DependencySorter] 实现。
 *
 * 排序步骤：
 * 1. 把 [Dependency.dependencies] / [Dependency.after] 解释为“前置边”， [Dependency.before] 解释为“后置边”；
 * 2. 使用 Kahn 算法生成拓扑序，每次从当前可用的节点中按 [comparator] 取一个， 因此 [Dependency.priority] 与 [Dependency.name]
 *    只用于打破平局；
 * 3. 遇到缺失引用或环时，按 [options] 的策略处理。
 *
 * 扩展方式：
 * - 覆写 [nameOf]、[priorityOf]、[precedingNamesOf]、[followingNamesOf] 可以适配 [Dependency] 不同子类的语义；
 * - 覆写 [comparator] 可以改变平局时的排序规则；
 * - 覆写 [onMissingReference]、[onCycle] 可以自定义异常或恢复策略。
 *
 * @param T 节点类型
 * @param options 冲突处理策略
 */
open class TopologicalDependencySorter<T : Dependency>(protected val options: Options = Options()) :
  DependencySorter<T> {
  /** 引用了当前批次中不存在的名字时的处理方式。 */
  enum class MissingReferencePolicy {
    /** 忽略这条边，把它当作可选依赖。 */
    IGNORE,

    /** 抛出 [IllegalArgumentException]。 */
    FAIL,
  }

  /** 排序结束后仍无法确定位置的节点（存在环）的处理方式。 */
  enum class CyclePolicy {
    /** 抛出 [DependencyCycleException]。 */
    FAIL,

    /** 把环中的节点按 [comparator] 追加到结果末尾，保证返回所有节点。 */
    APPEND_UNRESOLVED,
  }

  /**
   * @property missingReferencePolicy 缺失引用的处理方式
   * @property cyclePolicy 环的处理方式
   */
  data class Options(
    val missingReferencePolicy: MissingReferencePolicy = MissingReferencePolicy.IGNORE,
    val cyclePolicy: CyclePolicy = CyclePolicy.FAIL,
  )

  /**
   * 返回 [nodes] 的拓扑排序结果，不修改入参。
   *
   * 同一批次中的 [Dependency.name] 必须唯一，否则抛出 [IllegalArgumentException]。
   *
   * @throws IllegalArgumentException 名字重复，或缺失引用策略为 [MissingReferencePolicy.FAIL]
   * @throws DependencyCycleException 存在环且策略为 [CyclePolicy.FAIL]
   */
  override fun sort(nodes: Iterable<T>): List<T> {
    val entries = nodes.map { NodeEntry(it, nameOf(it)) }
    if (entries.isEmpty()) return emptyList()

    val byName = LinkedHashMap<String, NodeEntry<T>>(entries.size)
    for (entry in entries) {
      require(entry.name !in byName) { "Duplicate dependency name '${entry.name}'" }
      byName[entry.name] = entry
    }

    val outgoing = HashMap<String, MutableList<NodeEntry<T>>>(entries.size)
    val inDegree = HashMap<String, Int>(entries.size)
    for (entry in entries) {
      outgoing[entry.name] = mutableListOf()
      inDegree[entry.name] = 0
    }

    for (entry in entries) {
      for (preceding in precedingNamesOf(entry.node)) {
        if (preceding !in byName) {
          onMissingReference(entry.node, preceding)
          continue
        }
        outgoing.getValue(preceding).add(entry)
        inDegree[entry.name] = inDegree.getValue(entry.name) + 1
      }
      for (following in followingNamesOf(entry.node)) {
        val target = byName[following]
        if (target == null) {
          onMissingReference(entry.node, following)
          continue
        }
        outgoing.getValue(entry.name).add(target)
        inDegree[following] = inDegree.getValue(following) + 1
      }
    }

    val nodeComparator = comparator()
    val ready =
      PriorityQueue<NodeEntry<T>>(
        entries.size,
        Comparator { a, b -> nodeComparator.compare(a.node, b.node) },
      )
    for (entry in entries) {
      if (inDegree.getValue(entry.name) == 0) ready.add(entry)
    }

    val result = ArrayList<T>(entries.size)
    while (ready.isNotEmpty()) {
      val entry = ready.poll()
      result.add(entry.node)
      for (next in outgoing.getValue(entry.name)) {
        val remaining = inDegree.getValue(next.name) - 1
        inDegree[next.name] = remaining
        if (remaining == 0) ready.add(next)
      }
    }

    if (result.size == entries.size) return result

    val unresolved = entries.filter { inDegree.getValue(it.name) > 0 }.map { it.node }
    return onCycle(unresolved, result)
  }

  /** 返回节点名字；默认使用 [Dependency.name]。 */
  protected open fun nameOf(node: T): String = node.name

  /** 返回节点优先级；默认使用 [Dependency.priority]。 */
  protected open fun priorityOf(node: T): Int = node.priority

  /** 返回必须排在该节点之前的名字；默认合并 [Dependency.after] 与 [Dependency.dependencies]。 */
  protected open fun precedingNamesOf(node: T): Iterable<String> = node.precedingNames()

  /** 返回必须排在该节点之后的名字；默认使用 [Dependency.before]。 */
  protected open fun followingNamesOf(node: T): Iterable<String> = node.followingNames()

  /** 可用节点之间的排序规则；默认优先级高的在前，优先级相同则名字小的在前。 */
  protected open fun comparator(): Comparator<T> =
    compareByDescending<T> { priorityOf(it) }.thenBy { nameOf(it) }

  /** 找不到 [reference] 对应的节点时调用；默认在 [MissingReferencePolicy.FAIL] 下抛异常。 */
  protected open fun onMissingReference(owner: T, reference: String) {
    if (options.missingReferencePolicy == MissingReferencePolicy.FAIL) {
      throw IllegalArgumentException(
        "Unknown dependency reference '$reference' declared by '${nameOf(owner)}'"
      )
    }
  }

  /**
   * 拓扑排序结束后仍有节点无法定位时调用。
   *
   * @param unresolved 仍在环中或依赖环的节点，按输入顺序排列
   * @param ordered 已经排好的节点
   */
  protected open fun onCycle(unresolved: List<T>, ordered: List<T>): List<T> =
    when (options.cyclePolicy) {
      CyclePolicy.FAIL -> throw DependencyCycleException(unresolved.map { nameOf(it) })
      CyclePolicy.APPEND_UNRESOLVED -> ordered + unresolved.sortedWith(comparator())
    }

  private class NodeEntry<E>(val node: E, val name: String)
}

/** 依赖图存在环，且 [TopologicalDependencySorter.CyclePolicy.FAIL] 生效时抛出。 */
class DependencyCycleException(val names: List<String>) :
  IllegalStateException("Cyclic dependency detected among: ${names.joinToString()}")
