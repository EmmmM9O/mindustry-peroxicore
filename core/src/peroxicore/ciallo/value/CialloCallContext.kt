package peroxicore.ciallo.value

import peroxicore.ciallo.*

/**
 * Everything a builtin, a value member or a host native may do while it runs:
 * resolve arguments, run blocks, compare values and report errors.
 *
 * [environment] exposes the host services a component collaborates with
 * (output, sandbox, java bridge, value tables).
 */
class CialloCallContext(
  val interpreter: CialloInterpreter,
  val environment: CialloEnvironment,
  val scope: CialloScope,
) {
  fun resolve(value: CialloValue): CialloValue = interpreter.resolveForCall(value)

  fun runBlock(
    block: CialloValue,
    bindings: Map<String, CialloValue> = emptyMap(),
  ): CialloValue = interpreter.executeBlock(block(block, "block").block, bindings)

  /** Runs [block] in its closure plus [bindings], then restores the previous scope. */
  fun runBlockInScope(block: CialloBlockValue, bindings: Map<String, CialloValue> = emptyMap()): CialloValue {
    val scope = interpreter.scopeFor(block.block, bindings)
    return interpreter.executeBlockInScope(block.block, scope)
  }

  /**
   * Runs [block] in a custom scope: a child of the block's closure whose
   * [backing] (an object's fields) and [bindings] shadow the closure's variables.
   */
  fun <R> scopeWith(
    block: CialloValue,
    bindings: Map<String, CialloValue>,
    backing: CialloScopeBacking? = null,
    body: () -> R,
  ): R = interpreter.withBlockScope(block(block, "block").block, bindings, backing, body)

  fun format(value: CialloValue): String = CialloValues.formatValue(resolve(value))

  fun int(value: CialloValue, what: String): Int =
    when (val resolved = resolve(value)) {
      is CialloInt -> resolved.value
      is CialloFloat -> resolved.value.toInt()
      else -> error(what + " expects an Int, got " + CialloValues.formatValue(resolved))
    }

  fun index(value: CialloValue, size: Int): Int {
    val index = (resolve(value) as? CialloInt)?.value
      ?: throw CialloRuntimeException("index must be an Int")
    if (index !in 0 until size) throw CialloRuntimeException("index " + index + " is out of bounds")
    return index
  }

  fun float(value: CialloValue, what: String): Float =
    when (val resolved = resolve(value)) {
      is CialloInt -> resolved.value.toFloat()
      is CialloFloat -> resolved.value
      else -> error(what + " expects a number, got " + CialloValues.formatValue(resolved))
    }

  fun bool(value: CialloValue, what: String): Boolean =
    when (val resolved = resolve(value)) {
      is CialloBool -> resolved.value
      CialloNil.INSTANCE -> false
      is CialloInt -> resolved.value != 0
      is CialloFloat -> resolved.value != 0f
      is CialloStr -> resolved.value.isNotEmpty()
      else -> error(what + " expects a Bool/truthy value, got " + CialloValues.formatValue(resolved))
    }

  fun block(value: CialloValue, what: String): CialloBlockValue =
    resolve(value) as? CialloBlockValue ?: error(what + " expects a block")

  fun error(message: String): Nothing = throw CialloRuntimeException(message)

  /** Structural equality used by `==` and `Arr.contains`. */
  fun equals(left: CialloValue, right: CialloValue): Boolean = interpreter.equalValues(left, right)
}
