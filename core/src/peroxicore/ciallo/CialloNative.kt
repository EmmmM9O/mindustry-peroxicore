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
