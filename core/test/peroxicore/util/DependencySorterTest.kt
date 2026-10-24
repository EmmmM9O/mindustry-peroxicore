package peroxicore.util

import kotlin.test.*

class DependencySorterTest {
  private class Node(
    name: String,
    priority: Int = 0,
    after: Array<String> = emptyArray(),
    before: Array<String> = emptyArray(),
    dependencies: Array<String> = emptyArray(),
  ) : Dependency(name, priority, after, before, dependencies)

  private fun sequence(nodes: List<Dependency>): List<String> = nodes.map { it.name }

  @Test
  fun ordersDependenciesBeforeDependents() {
    val sorted =
      TopologicalDependencySorter<Node>()
        .sort(
          listOf(
            Node("a", dependencies = arrayOf("b")),
            Node("b", dependencies = arrayOf("c")),
            Node("c"),
          )
        )

    assertEquals(listOf("c", "b", "a"), sequence(sorted))
  }

  @Test
  fun treatsAfterAsPrerequisite() {
    val sorted =
      TopologicalDependencySorter<Node>()
        .sort(
          listOf(
            Node("a", after = arrayOf("z")),
            Node("z"),
            Node("m"),
          )
        )

    assertEquals(listOf("m", "z", "a"), sequence(sorted))
  }

  @Test
  fun treatsBeforeAsSuccessor() {
    val sorted =
      TopologicalDependencySorter<Node>()
        .sort(
          listOf(
            Node("z", before = arrayOf("a")),
            Node("a"),
            Node("m"),
          )
        )

    assertEquals(listOf("m", "z", "a"), sequence(sorted))
  }

  @Test
  fun breaksTiesByPriorityThenName() {
    val sorted =
      TopologicalDependencySorter<Node>()
        .sort(
          listOf(
            Node("a"),
            Node("b", priority = 10),
            Node("c", priority = 5),
          )
        )

    assertEquals(listOf("b", "c", "a"), sequence(sorted))
  }

  @Test
  fun dependencyOverridesPriority() {
    val sorted =
      TopologicalDependencySorter<Node>()
        .sort(
          listOf(
            Node("high", priority = 100, dependencies = arrayOf("low")),
            Node("low"),
            Node("mid", priority = 50),
          )
        )

    assertEquals(listOf("mid", "low", "high"), sequence(sorted))
  }

  @Test
  fun ignoresMissingReferencesByDefault() {
    val sorted =
      TopologicalDependencySorter<Node>()
        .sort(
          listOf(
            Node("a", dependencies = arrayOf("missing")),
            Node("b"),
          )
        )

    assertEquals(listOf("a", "b"), sequence(sorted))
  }

  @Test
  fun canFailOnMissingReferences() {
    assertFailsWith<IllegalArgumentException> {
      TopologicalDependencySorter<Node>(
        TopologicalDependencySorter.Options(
          missingReferencePolicy = TopologicalDependencySorter.MissingReferencePolicy.FAIL,
        )
      )
        .sort(listOf(Node("a", dependencies = arrayOf("missing"))))
    }
  }

  @Test
  fun reportsCycleByDefault() {
    val error =
      assertFailsWith<DependencyCycleException> {
        TopologicalDependencySorter<Node>()
          .sort(
            listOf(
              Node("a", dependencies = arrayOf("b")),
              Node("b", dependencies = arrayOf("a")),
            )
          )
      }

    assertEquals(setOf("a", "b"), error.names.toSet())
  }

  @Test
  fun canAppendUnresolvedCycle() {
    val sorted =
      TopologicalDependencySorter<Node>(
        TopologicalDependencySorter.Options(
          cyclePolicy = TopologicalDependencySorter.CyclePolicy.APPEND_UNRESOLVED,
        )
      )
        .sort(
          listOf(
            Node("a", dependencies = arrayOf("b")),
            Node("b", dependencies = arrayOf("a")),
            Node("c"),
          )
        )

    assertEquals("c", sorted.first().name)
    assertEquals(setOf("a", "b", "c"), sequence(sorted).toSet())
  }

  @Test
  fun rejectsDuplicateNames() {
    assertFailsWith<IllegalArgumentException> {
      TopologicalDependencySorter<Node>().sort(listOf(Node("a"), Node("a")))
    }
  }

  @Test
  fun sortsInPlaceThroughAbstraction() {
    val sorter: DependencySorter<Node> = TopologicalDependencySorter()
    val nodes = mutableListOf(Node("a", dependencies = arrayOf("b")), Node("b"))

    val returned = sorter.sortInPlace(nodes)

    assertSame(nodes, returned)
    assertEquals(listOf("b", "a"), sequence(nodes))
  }

  @Test
  fun customImplementationCanProvideDifferentOrder() {
    val sorter = DependencySorter<Node> { nodes -> nodes.sortedByDescending { it.name } }

    val sorted = sorter.sort(listOf(Node("a"), Node("b")))

    assertEquals(listOf("b", "a"), sequence(sorted))
  }

  @Test
  fun doesNotModifyInput() {
    val input = listOf(Node("a", dependencies = arrayOf("b")), Node("b"))

    TopologicalDependencySorter<Node>().sort(input)

    assertEquals(listOf("a", "b"), sequence(input))
  }

  @Test
  fun subclassCanCustomizeTieBreaker() {
    val sorter =
      object : TopologicalDependencySorter<Node>() {
        override fun priorityOf(node: Node): Int = -node.priority
      }

    val sorted = sorter.sort(listOf(Node("a"), Node("b", priority = 10)))

    assertEquals(listOf("a", "b"), sequence(sorted))
  }
}
