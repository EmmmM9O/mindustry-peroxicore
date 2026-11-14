package peroxicore.ciallo.value

import peroxicore.ciallo.*

/** Numeric semantics shared by [CialloInt] and [CialloFloat]. */
object CialloNumbers {
  fun number(value: CialloValue): CialloNumber =
    value as? CialloNumber ?: throw CialloRuntimeException("expected a number, got " + value.render())

  fun binary(left: CialloValue, right: CialloValue, operator: String): CialloValue {
    val leftNumber = number(left)
    val rightNumber = number(right)
    val a = leftNumber.asDouble()
    val b = rightNumber.asDouble()
    val result =
      when (operator) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> divide(a, b)
        "%" -> {
          divide(a, b)
          a % b
        }
        else -> throw CialloRuntimeException("unknown numeric operator " + operator)
      }
    return if (leftNumber.kind == CialloKind.Int && rightNumber.kind == CialloKind.Int) {
      CialloInt(result.toInt())
    } else {
      CialloFloat(result.toFloat())
    }
  }

  private fun divide(a: Double, b: Double): Double {
    if (b == 0.0) throw CialloRuntimeException("division by zero")
    return a / b
  }
}

private val numericTable: CialloMemberTable = memberTable {
  method("add") { _, value, args ->
    requireArgs("add", args, 1)
    CialloNumbers.binary(value, args[0], "+")
  }

  method("plus") { _, value, args ->
    requireArgs("plus", args, 1)
    CialloNumbers.binary(value, args[0], "+")
  }

  method("sub") { _, value, args ->
    requireArgs("sub", args, 1)
    CialloNumbers.binary(value, args[0], "-")
  }

  method("minus") { _, value, args ->
    requireArgs("minus", args, 1)
    CialloNumbers.binary(value, args[0], "-")
  }

  method("mul") { _, value, args ->
    requireArgs("mul", args, 1)
    CialloNumbers.binary(value, args[0], "*")
  }

  method("times") { _, value, args ->
    requireArgs("times", args, 1)
    CialloNumbers.binary(value, args[0], "*")
  }

  method("div") { _, value, args ->
    requireArgs("div", args, 1)
    CialloNumbers.binary(value, args[0], "/")
  }

  method("mod") { _, value, args ->
    requireArgs("mod", args, 1)
    CialloNumbers.binary(value, args[0], "%")
  }

  method("less") { _, value, args ->
    requireArgs("less", args, 1)
    CialloBool(CialloNumbers.number(value).asDouble() < CialloNumbers.number(args[0]).asDouble())
  }

  method("lessEq") { _, value, args ->
    requireArgs("lessEq", args, 1)
    CialloBool(CialloNumbers.number(value).asDouble() <= CialloNumbers.number(args[0]).asDouble())
  }

  method("greater") { _, value, args ->
    requireArgs("greater", args, 1)
    CialloBool(CialloNumbers.number(value).asDouble() > CialloNumbers.number(args[0]).asDouble())
  }

  method("greaterEq") { _, value, args ->
    requireArgs("greaterEq", args, 1)
    CialloBool(CialloNumbers.number(value).asDouble() >= CialloNumbers.number(args[0]).asDouble())
  }

  method("eq") { _, value, args ->
    requireArgs("eq", args, 1)
    CialloBool(CialloNumbers.number(value).asDouble() == CialloNumbers.number(args[0]).asDouble())
  }

  method("ne") { _, value, args ->
    requireArgs("ne", args, 1)
    CialloBool(CialloNumbers.number(value).asDouble() != CialloNumbers.number(args[0]).asDouble())
  }

  method("negate") { _, value, _ -> CialloNumbers.binary(CialloInt(0), value, "-") }

  method("toInt") { _, value, _ -> CialloInt(CialloNumbers.number(value).asDouble().toInt()) }

  method("toFloat") { _, value, _ -> CialloFloat(CialloNumbers.number(value).asDouble().toFloat()) }

  method("toString") { _, value, _ -> CialloStr(value.render()) }

  property("sign") { _, value -> CialloInt(CialloNumbers.number(value).asDouble().let { if (it > 0) 1 else if (it < 0) -1 else 0 }) }

  property("isZero") { _, value -> CialloBool(CialloNumbers.number(value).asDouble() == 0.0) }

  property("isNaN") { _, value -> CialloBool(CialloNumbers.number(value).asDouble().isNaN()) }
}

/** Members of [CialloInt] and [CialloFloat]. */
object CialloNumericMembers : CialloMembers by numericTable
