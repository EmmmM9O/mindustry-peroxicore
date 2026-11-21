package peroxicore.ciallo.host

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/**
 * A set of host natives exposed as the variables of one block scope.
 *
 * Generated bindings and hand-written scope helpers write here, and
 * [runInScope] executes a script block against it.  Because the map is a
 * [CialloScopeBacking], a binding is visible inside the block and its nested
 * blocks but never leaks into the environment's global names.
 *
 * Registering the same scope twice (a world block and every keyframe share one
 * scene) is fine: names are replaced, so the freshest binding wins.
 */
class CialloNatives {
  val fields: MutableMap<String, CialloValue> = linkedMapOf()

  /** Registers `name(args)`. */
  fun function(
    name: String,
    body: (CialloCallContext, List<CialloValue>) -> CialloValue,
  ) {
    fields[name] = CialloNativeValue(name, CialloNative(name, CialloNativeFunction(body)))
  }

  /** Registers a readable/writable name that is not a function. */
  fun value(
    name: String,
    value: CialloValue,
  ) {
    fields[name] = value
  }

  /** A target for [CialloHostBinder], so generated bindings can publish into this scope. */
  fun target(): CialloNativeTarget =
    CialloNativeTarget { name, function -> fields[name] = CialloNativeValue(name, CialloNative(name, function)) }
}

/** Exposes a [CialloNatives] map as the backing store of a scope. */
class CialloNativesBacking(
  private val natives: CialloNatives,
) : CialloScopeBacking {
  override fun get(name: String): CialloValue? = natives.fields[name]

  override fun contains(name: String): Boolean = natives.fields.containsKey(name)

  override fun set(name: String, value: CialloValue): Boolean {
    if (!natives.fields.containsKey(name)) return false
    natives.fields[name] = value
    return true
  }
}

/**
 * Runs a stored block in a child of its own closure whose [natives] shadow it.
 *
 * A fresh interpreter is created on every call: Ciallo blocks keep their
 * statements and closure, so deferred execution (a Ponder keyframe replayed each
 * loop) needs no interpreter from build time.  [bindings] are defined as locals
 * on top so a host can expose `it` or any context value.
 */
fun CialloEnvironment.runInScope(
  block: CialloBlockValue,
  natives: CialloNatives,
  bindings: Map<String, CialloValue> = emptyMap(),
): CialloValue {
  val interpreter = CialloInterpreter(this, globalScope)
  val scope = block.block.closure.child(CialloNativesBacking(natives))
  scope.defineAll(bindings)
  return interpreter.executeBlockInScope(block.block, scope)
}
