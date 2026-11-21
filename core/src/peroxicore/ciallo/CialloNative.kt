package peroxicore.ciallo

import peroxicore.ciallo.value.*

/** Strict parameter/value types used by native overload dispatch. */
enum class CialloType(val displayName: String) {
  Any("Any"),
  Int("Int"),
  Float("Float"),
  Time("Time"),
  Str("String"),
  Bool("Bool"),
  Nil("Nil"),
  Arr("Array"),
  Symbol("Symbol"),
  Obj("Obj"),
  Block("Block"),
  Native("Native"),
  Java("Java"),
  Host("Host"),
  Other("Value");

  fun matches(value: CialloValue): Boolean =
    when (this) {
      Any -> true
      Int -> value is CialloInt
      Float -> value is CialloFloat
      Time -> value is CialloTime
      Str -> value is CialloStr
      Bool -> value is CialloBool
      Nil -> value === CialloNil.INSTANCE
      Arr -> value is CialloArr
      Symbol -> value is CialloSymbol
      Obj -> value is CialloObj
      Block -> value is CialloBlockValue
      Native -> value is CialloNativeValue
      Java -> value is CialloJavaValue
      Host -> value is CialloHostValue
      Other -> false
    }
}

val CialloValue.type: CialloType
  get() =
    when (kind) {
      CialloKind.Nil -> CialloType.Nil
      CialloKind.Bool -> CialloType.Bool
      CialloKind.Int -> CialloType.Int
      CialloKind.Float -> CialloType.Float
      CialloKind.Time -> CialloType.Time
      CialloKind.Str -> CialloType.Str
      CialloKind.Arr -> CialloType.Arr
      CialloKind.Symbol -> CialloType.Symbol
      CialloKind.Obj -> CialloType.Obj
      CialloKind.Block -> CialloType.Block
      CialloKind.Native -> CialloType.Native
      CialloKind.Java -> CialloType.Java
      CialloKind.Host -> CialloType.Host
    }

private class CialloNativeOverload(
  val parameters: List<CialloType>?,
  val function: CialloNativeFunction,
)

/** Whether this parameter list matches [values] by arity and exact [CialloType]. */
fun List<CialloType>.matchesValues(values: List<CialloValue>): Boolean =
  size == values.size && indices.all { this[it].matches(values[it]) }

/**
 * Picks the most specific overload that matches [values].
 *
 * A parameter list without [CialloType.Any] wins over one that uses it, so a strict overload is
 * preferred over a catch-all; otherwise the first match wins.
 */
fun <T> selectOverload(
  overloads: List<Pair<List<CialloType>, T>>,
  values: List<CialloValue>,
): T? {
  val matches = overloads.filter { (parameters, _) -> parameters.matchesValues(values) }
  return (matches.firstOrNull { (parameters, _) -> parameters.none { it == CialloType.Any } }
    ?: matches.firstOrNull())?.second
}

/**
 * The shared "no overload" diagnostic, such as
 * `function 'at' has no overload for (Int, Float); expected (Int, Int) or (Float, Float)`.
 */
fun noOverloadMessage(
  what: String,
  expected: Collection<List<CialloType>>,
  values: List<CialloValue>,
): String {
  val actual = values.joinToString(", ") { it.type.displayName }
  val suffix =
    if (expected.isEmpty()) {
      ""
    } else {
      "; expected " + expected.joinToString(" or ") { it.joinToString(", ", "(", ")") { type -> type.displayName } }
    }
  return what + " has no overload for (" + actual + ")" + suffix
}

/**
 * A native function with one or more optional strict overloads.
 *
 * An untyped overload (registered through [CialloEnvironment.native]) receives the
 * raw arguments.  Typed overloads receive resolved arguments and only match when
 * every [CialloType] matches exactly.
 */
class CialloNative(val name: String) {
  private val overloads = mutableListOf<CialloNativeOverload>()

  constructor(name: String, function: CialloNativeFunction) : this(name) {
    add(function)
  }

  fun add(function: CialloNativeFunction) {
    overloads += CialloNativeOverload(null, function)
  }

  fun add(parameters: List<CialloType>, function: CialloNativeFunction) {
    overloads += CialloNativeOverload(parameters, function)
  }

  fun invoke(context: CialloCallContext, args: List<CialloValue>): CialloValue {
    val typed = overloads.mapNotNull { overload ->
      overload.parameters?.let { it to overload.function }
    }
    val resolved = if (typed.isNotEmpty()) args.map { context.resolve(it) } else args

    selectOverload(typed, resolved)?.let { return it.invoke(context, resolved) }

    val untyped = overloads.firstOrNull { it.parameters == null }
    if (untyped != null) {
      return untyped.function.invoke(context, args)
    }

    throw CialloRuntimeException(
      noOverloadMessage("function '" + name + "'", typed.map { it.first }, resolved),
    )
  }
}
