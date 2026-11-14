package peroxicore.ciallo.value

import peroxicore.ciallo.*
import peroxicore.parsers.*

enum class CialloTimeUnit(
  val suffix: String,
  val millis: Long,
) {
  Second("s", 1_000L),
  Minute("m", 60_000L),
  Hour("h", 3_600_000L),
}

/** A block of Ciallo statements together with the scope in which it was defined. */
class CialloBlock(
  val statements: List<CialloParser.StatementContext>,
  val closure: CialloScope,
)

fun interface CialloNativeFunction {
  fun invoke(context: CialloCallContext, args: List<CialloValue>): CialloValue
}

/** The family of a [CialloValue]; used for display and native overload dispatch. */
enum class CialloKind(val displayName: String) {
  Nil("Nil"),
  Bool("Bool"),
  Int("Int"),
  Float("Float"),
  Time("Time"),
  Str("String"),
  Arr("Array"),
  Symbol("Symbol"),
  Obj("Obj"),
  Block("Block"),
  Native("Native"),
  Java("Java")
}

/**
 * How a value type handles members: `value.name` and `value.name(args)`.
 *
 * A step returns `null` when it does not handle the member and never throws, so
 * a container can hand a miss down to the members of the value it wraps, and a
 * value family can install a fallback (the JVM bridge of [JavaValue]) without
 * the interpreter knowing about it.
 */
interface CialloMembers {
  fun member(context: CialloCallContext, value: CialloValue, name: String): CialloValue? = null

  fun call(
    context: CialloCallContext,
    value: CialloValue,
    name: String,
    args: List<CialloValue>,
  ): CialloValue? = null
}

/** Values that own named properties; used by completion and documentation. */
interface CialloPropertySource {
  fun propertyNames(): Collection<String>
}

/** Runtime values understood by Ciallo. */
interface CialloValue {
  val kind: CialloKind

  val members: CialloMembers

  /** This value exactly as written, without interpolating or resolving embedded symbols. */
  fun display(): String

  /**
   * The receiver of [members].  A value that refers to another value (a
   * [CialloSymbol]) returns the referred value, so members answer for what it
   * names; anything else returns itself.
   */
  fun memberTarget(): CialloValue = this

  /** Assigns a member; returns `false` when the value has no such member. */
  fun setMember(context: CialloCallContext, name: String, value: CialloValue): Boolean = false

  companion object {
    fun of(boolean: Boolean): CialloValue = CialloBool(boolean)

    fun of(number: Int): CialloValue = CialloInt(number)

    fun of(number: Float): CialloValue = CialloFloat(number)

    fun of(text: String): CialloValue = CialloStr(text)

    fun nil(): CialloValue = CialloNil.INSTANCE
  }
}


