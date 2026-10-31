package peroxicore.ciallo

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
  Other("Value");

  fun matches(value: CialloValue): Boolean =
    when (this) {
      Any -> true
      Int -> value is CialloValue.IntValue
      Float -> value is CialloValue.FloatValue
      Time -> value is CialloValue.TimeValue
      Str -> value is CialloValue.Str
      Bool -> value is CialloValue.Bool
      Nil -> value === CialloValue.Nil
      Arr -> value is CialloValue.Arr
      Symbol -> value is CialloValue.Symbol
      Obj -> value is CialloValue.Obj
      Block -> value is CialloValue.BlockValue
      Native -> value is CialloValue.NativeValue
      Java -> value is CialloValue.JavaValue
      Other -> false
    }
}

val CialloValue.type: CialloType
  get() =
    when (this) {
      CialloValue.Nil -> CialloType.Nil
      is CialloValue.Bool -> CialloType.Bool
      is CialloValue.IntValue -> CialloType.Int
      is CialloValue.FloatValue -> CialloType.Float
      is CialloValue.TimeValue -> CialloType.Time
      is CialloValue.Str -> CialloType.Str
      is CialloValue.Arr -> CialloType.Arr
      is CialloValue.Symbol -> CialloType.Symbol
      is CialloValue.Obj -> CialloType.Obj
      is CialloValue.BlockValue -> CialloType.Block
      is CialloValue.NativeValue -> CialloType.Native
      is CialloValue.JavaValue -> CialloType.Java
    }

private class CialloNativeOverload(
  val parameters: List<CialloType>?,
  val function: CialloNativeFunction,
)

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
    val typed = overloads.filter { it.parameters != null }
    val resolved = if (typed.isNotEmpty()) args.map { context.resolve(it) } else args

    if (typed.isNotEmpty()) {
      val matches =
        typed.filter { overload ->
          val parameters = overload.parameters!!
          parameters.size == resolved.size &&
            parameters.indices.all { index -> parameters[index].matches(resolved[index]) }
        }
      val matched =
        matches.firstOrNull { overload -> overload.parameters!!.none { it == CialloType.Any } }
          ?: matches.firstOrNull()
      if (matched != null) {
        return matched.function.invoke(context, resolved)
      }
    }

    val untyped = overloads.firstOrNull { it.parameters == null }
    if (untyped != null) {
      return untyped.function.invoke(context, args)
    }

    val actual = resolved.joinToString(", ") { it.type.displayName }
    val expected =
      typed.joinToString(" or ") { overload ->
        overload.parameters!!.joinToString(", ", "(", ")") { it.displayName }
      }
    val suffix = if (expected.isEmpty()) "" else "; expected " + expected
    throw CialloRuntimeException(
      "function '" + name + "' has no overload for (" + actual + ")" + suffix,
    )
  }
}
