package peroxicore.ponder.api.ciallo

import peroxicore.ciallo.*
import peroxicore.ciallo.ops.*
import peroxicore.ciallo.value.*

/**
 * Resolves `@Blocks`, `@Items`, ... and their members.
 *
 * Content families are not globals, so `@Blocks.copperWall` reaches this
 * resolver before the JVM bridge: the path's head selects a namespace and the
 * rest is read through its member table (which maps camelCase to Mindustry's
 * kebab-case names).
 */
class CialloPonderResolver : CialloNameResolver {
  override val label: String get() = "ponder content"

  private val namespaces by lazy { contentNamespaces() }

  override fun resolve(
    interpreter: CialloInterpreter,
    path: List<String>,
  ): CialloValue? {
    if (path.isEmpty()) return null
    val namespace = namespaces[path.first()] ?: return null
    if (path.size == 1) return namespace
    return interpreter.memberPath(namespace, path.drop(1))
  }
}
