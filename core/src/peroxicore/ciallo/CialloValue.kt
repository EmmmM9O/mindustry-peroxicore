package peroxicore.ciallo

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

/** Runtime values understood by Ciallo. */
sealed interface CialloValue {
  data object Nil : CialloValue

  data class Bool(val value: Boolean) : CialloValue

  data class IntValue(val value: Int) : CialloValue

  data class FloatValue(val value: Float) : CialloValue

  data class TimeValue(val amount: Double, val unit: CialloTimeUnit) : CialloValue {
    val millis: Long get() = (amount * unit.millis).toLong()
  }

  data class Str(val value: String) : CialloValue

  data class Arr(val items: MutableList<CialloValue> = mutableListOf()) : CialloValue

  /** A name literal such as @x or @Blocks.duo.  It is a reference, not a value. */
  data class Symbol(val path: List<String>) : CialloValue {
    val name: String get() = path.joinToString(".")
  }

  data class Obj(val fields: MutableMap<String, CialloValue> = linkedMapOf()) : CialloValue

  data class BlockValue(val block: CialloBlock) : CialloValue

  data class NativeValue(val name: String, val native: CialloNative) : CialloValue

  /** A host JVM object.  Access is guarded by [CialloSandbox]. */
  data class JavaValue(val value: Any) : CialloValue

  /*
  data class Point(val x: Float, val y: Float) : CialloValue

  data class Vec2(val x: Float, val y: Float) : CialloValue

  class BuildAction(
    val block: CialloValue,
    var position: CialloValue? = null,
    var config: CialloValue? = null,
  ) : CialloValue

  class LabelAction(
    val text: String,
    var position: CialloValue? = null,
    var duration: TimeValue? = null,
  ) : CialloValue

  class FrameAction(
    val time: TimeValue? = null,
    val actions: MutableList<CialloValue> = mutableListOf(),
  ) : CialloValue
  */
}

fun CialloValue.format(): String =
  when (this) {
    CialloValue.Nil -> "null"
    is CialloValue.Bool -> value.toString()
    is CialloValue.IntValue -> value.toString()
    is CialloValue.FloatValue -> value.toString()
    is CialloValue.TimeValue -> {
      val number =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString()
        else amount.toString()
      number + unit.suffix
    }
    is CialloValue.Str -> value
    is CialloValue.Arr -> items.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.format() }
    is CialloValue.Symbol -> name
    is CialloValue.Obj -> fields.entries.joinToString(prefix = "{", postfix = "}", separator = ", ") {
      it.key + ": " + it.value.format()
    }
    is CialloValue.BlockValue -> "<block>"
    is CialloValue.NativeValue -> "<native:" + name + ">"
    is CialloValue.JavaValue -> value.toString()
    /*
    is CialloValue.Point -> "Point(" + x + ", " + y + ")"
    is CialloValue.Vec2 -> "Vec2(" + x + ", " + y + ")"
    is CialloValue.BuildAction -> "<build " + block.format() + ">"
    is CialloValue.LabelAction -> "<label:" + text + ">"
    is CialloValue.FrameAction -> "<frame>"*/
  }
