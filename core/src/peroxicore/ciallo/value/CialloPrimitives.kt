package peroxicore.ciallo.value

import peroxicore.ciallo.*

enum class CialloNil : CialloValue {
  INSTANCE;

  override val kind: CialloKind get() = CialloKind.Nil

  override val members: CialloMembers get() = CialloNilMembers

  override fun display(): String = "null"
}

data class CialloBool(val value: Boolean) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Bool

  override val members: CialloMembers get() = CialloBoolMembers

  override fun display(): String = value.toString()
}

/** Int and Float are distinct kinds; mixed arithmetic yields Float. */
interface CialloNumber : CialloValue {
  fun asDouble(): Double
}

data class CialloInt(val value: Int) : CialloNumber {
  override val kind: CialloKind get() = CialloKind.Int

  override val members: CialloMembers get() = CialloNumericMembers

  override fun display(): String = value.toString()

  override fun asDouble(): Double = value.toDouble()
}

data class CialloFloat(val value: Float) : CialloNumber {
  override val kind: CialloKind get() = CialloKind.Float

  override val members: CialloMembers get() = CialloNumericMembers

  override fun display(): String = value.toString()

  override fun asDouble(): Double = value.toDouble()
}

data class CialloTime(val amount: Double, val unit: CialloTimeUnit) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Time

  override val members: CialloMembers get() = CialloTimeMembers

  val millis: Long get() = (amount * unit.millis).toLong()

  override fun display(): String {
    val number = if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    return number + unit.suffix
  }
}

data class CialloStr(val value: String) : CialloValue, CialloPropertySource {
  override val kind: CialloKind get() = CialloKind.Str

  override val members: CialloMembers get() = CialloStrMembers

  override fun display(): String = value

  override fun propertyNames(): Collection<String> = CialloStrMembers.memberTableView().exposedNames()
}

data class CialloArr(val items: MutableList<CialloValue> = mutableListOf()) : CialloValue, CialloPropertySource {
  override val kind: CialloKind get() = CialloKind.Arr

  override val members: CialloMembers get() = CialloArrMembers

  override fun display(): String = items.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.display() }

  override fun propertyNames(): Collection<String> = CialloArrMembers.memberTableView().exposedNames()
}

/** A name literal such as @x or @Blocks.duo.  It is a reference, not a value. */
data class CialloSymbol(val path: List<String>) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Symbol

  override val members: CialloMembers get() = CialloSymbolMembers

  val name: String get() = path.joinToString(".")

  override fun display(): String = name

  override fun memberTarget(): CialloValue = this
}

class CialloObj(
  val fields: MutableMap<String, CialloValue> = linkedMapOf(),
) : CialloValue, CialloPropertySource {
  override val kind: CialloKind get() = CialloKind.Obj

  override val members: CialloMembers get() = CialloObjMembers

  override fun display(): String =
    fields.entries.joinToString(prefix = "{", postfix = "}", separator = ", ") { it.key + ": " + it.value.display() }

  override fun propertyNames(): Collection<String> = fields.keys

  override fun setMember(context: CialloCallContext, name: String, value: CialloValue): Boolean {
    if (!fields.containsKey(name)) return false
    fields[name] = value
    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CialloObj) return false
    return fields == other.fields
  }

  override fun hashCode(): Int = fields.hashCode()
}

data class CialloBlockValue(val block: CialloBlock) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Block

  override val members: CialloMembers get() = CialloBlockMembers

  override fun display(): String = "<block>"
}

data class CialloNativeValue(val name: String, val native: CialloNative) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Native

  override val members: CialloMembers get() = CialloNativeMembers

  override fun display(): String = "<native:$name>"
}

/** A host JVM object.  Access goes through [CialloJavaBridge] and the sandbox. */
class CialloJavaValue(val value: Any) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Java

  override val members: CialloMembers get() = CialloJavaMembers

  override fun display(): String = value.toString()

  override fun equals(other: Any?): Boolean = other is CialloJavaValue && other.value === value

  override fun hashCode(): Int = System.identityHashCode(value)
}

val CialloKind.isNumber: Boolean get() = this == CialloKind.Int || this == CialloKind.Float
