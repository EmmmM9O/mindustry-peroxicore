package peroxicore.ciallo.value

import peroxicore.ciallo.*

/**
 * Value-level helpers shared by components, the interpreter and the host.
 *
 * Each value type renders itself ([CialloValue.display]); [renderHooks] lets a
 * host adjust the text afterwards (for example to redact host objects).
 */
object CialloValues {
  /** Renders a value the way string interpolation and `print` do. */
  fun formatValue(value: CialloValue): String {
    val rendered = value.display()
    val hook = renderHooks.lastOrNull() ?: return rendered
    return hook.render(value, rendered) ?: rendered
  }

  /** Host-provided post-processing of rendered values; later hooks win. */
  val renderHooks: MutableList<CialloRenderHook> = mutableListOf()

  fun truthy(context: CialloCallContext, value: CialloValue): Boolean =
    when (val resolved = context.resolve(value)) {
      is CialloBool -> resolved.value
      CialloNil.INSTANCE -> false
      is CialloInt -> resolved.value != 0
      is CialloFloat -> resolved.value != 0f
      is CialloStr -> resolved.value.isNotEmpty()
      else -> true
    }

  /** Numbers compare by value across Int/Float; everything else compares structurally. */
  fun equal(left: CialloValue, right: CialloValue): Boolean {
    if (left.kind.isNumber && right.kind.isNumber) {
      return CialloNumbers.number(left).asDouble() == CialloNumbers.number(right).asDouble()
    }
    return left == right
  }
}

/** Adjusts the rendered text of a value; return [rendered] to keep it. */
fun interface CialloRenderHook {
  fun render(value: CialloValue, rendered: String): String?
}
