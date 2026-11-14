package peroxicore.ciallo.value

/**
 * Renders [this] the way string interpolation and `print` do: the value's own
 * [CialloValue.display], with the [peroxicore.ciallo.ops.CialloOperations] hooks
 * applied when they are registered.
 *
 * Named `render` rather than `format` so it never competes with
 * `java.lang.String.format`.
 */
fun CialloValue.render(): String = CialloValues.formatValue(this)
