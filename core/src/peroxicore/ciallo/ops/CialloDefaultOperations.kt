package peroxicore.ciallo.ops

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/** Arithmetic on [CialloNumber] values; mixed Int/Float yields Float. */
object CialloArithmetic : CialloBinaryOperator {
  override val label: String get() = "numbers"

  override fun accepts(operator: String, left: CialloValue, right: CialloValue): Boolean =
    operator in arithmeticOperators &&
      left is CialloNumber &&
      right is CialloNumber

  override fun apply(
    context: CialloCallContext,
    operator: String,
    left: CialloValue,
    right: CialloValue,
  ): CialloValue? = CialloArithmetic.binary(left, right, operator)

  fun binary(left: CialloValue, right: CialloValue, operator: String): CialloValue {
    val leftNumber = left as CialloNumber
    val rightNumber = right as CialloNumber
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
        "==" -> return CialloBool(a == b)
        "!=" -> return CialloBool(a != b)
        "<" -> return CialloBool(a < b)
        "<=" -> return CialloBool(a <= b)
        ">" -> return CialloBool(a > b)
        ">=" -> return CialloBool(a >= b)
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

  private val arithmeticOperators = setOf("+", "-", "*", "/", "%", "==", "!=", "<", "<=", ">", ">=")
}

/** `a + b` concatenates when the left side is a string, otherwise falls back to numbers. */
object CialloConcatenation : CialloBinaryOperator {
  override val label: String get() = "concatenation"

  override fun accepts(operator: String, left: CialloValue, right: CialloValue): Boolean =
    operator == "+" && left is CialloStr

  override fun apply(
    context: CialloCallContext,
    operator: String,
    left: CialloValue,
    right: CialloValue,
  ): CialloValue? = CialloStr((left as CialloStr).value + right.render())
}

/** Structural equality and ordering for non-numbers and mixed numbers. */
object CialloOrdering : CialloBinaryOperator {
  override val label: String get() = "ordering"

  override fun accepts(operator: String, left: CialloValue, right: CialloValue): Boolean =
    operator in operators

  override fun apply(
    context: CialloCallContext,
    operator: String,
    left: CialloValue,
    right: CialloValue,
  ): CialloValue? =
    when (operator) {
      "==" -> CialloBool(context.interpreter.equalValues(left, right))
      "!=" -> CialloBool(!context.interpreter.equalValues(left, right))
      "<", "<=", ">", ">=" -> {
        val a = CialloNumbers.number(left).asDouble()
        val b = CialloNumbers.number(right).asDouble()
        CialloBool(
          when (operator) {
            "<" -> a < b
            "<=" -> a <= b
            ">" -> a > b
            else -> a >= b
          }
        )
      }
      else -> null
    }

  private val operators = setOf("==", "!=", "<", "<=", ">", ">=")
}

/** Unary `-` on numbers. */
object CialloNegation : CialloPrefixOperator {
  override val label: String get() = "negation"

  override fun accepts(operator: String, operand: CialloValue): Boolean =
    operator == "-" && operand is CialloNumber

  override fun apply(context: CialloCallContext, operator: String, operand: CialloValue): CialloValue? =
    when (val number = operand as CialloNumber) {
      is CialloInt -> CialloInt(-number.value)
      is CialloFloat -> CialloFloat(-number.value)
      else -> throw CialloRuntimeException("unary '-' expects a number, got " + number.render())
    }
}

/** Unary `!` on truthy values. */
object CialloLogicalNot : CialloPrefixOperator {
  override val label: String get() = "not"

  override fun accepts(operator: String, operand: CialloValue): Boolean = operator == "!"

  override fun apply(context: CialloCallContext, operator: String, operand: CialloValue): CialloValue? =
    CialloBool(!CialloValues.truthy(context, operand))
}

/**
 * `@x` looks the name up in the scopes, then in the registered symbols, then in
 * the [CialloJavaBridge] (static fields in full access mode).
 */
object CialloSymbolNames : CialloNameResolver {
  override val label: String get() = "scopes"

  override fun resolve(interpreter: CialloInterpreter, path: List<String>): CialloValue? {
    if (path.isEmpty()) return null
    val environment = interpreter.environment
    interpreter.currentScope.lookup(path.first())?.let { return interpreter.memberPath(it, path.drop(1)) }
    environment.symbols[path.joinToString(".")]?.let { return it }
    environment.symbols[path.first()]?.let { return interpreter.memberPath(it, path.drop(1)) }
    if (environment.sandbox.allowJvmAccess) {
      environment.javaBridge.resolveStatic(environment.sandbox, path)?.let { return it }
    }
    return null
  }
}
