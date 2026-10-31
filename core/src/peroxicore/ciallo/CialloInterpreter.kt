package peroxicore.ciallo

import org.antlr.v4.kotlinruntime.*
import peroxicore.parsers.*

/**
 * Everything a builtin may do while it is running: resolve arguments, execute
 * blocks and report errors.
 */
class CialloCallContext(
  val interpreter: CialloInterpreter,
  val environment: CialloEnvironment,
  val scope: CialloScope,
) {
  fun resolve(value: CialloValue): CialloValue = interpreter.resolveForCall(value)

  fun runBlock(
    block: CialloValue.BlockValue,
    bindings: Map<String, CialloValue> = emptyMap(),
  ): CialloValue = interpreter.executeBlock(block.block, bindings)

  fun format(value: CialloValue): String = resolve(value).format()

  fun int(value: CialloValue, what: String): Int =
    when (val resolved = resolve(value)) {
      is CialloValue.IntValue -> resolved.value
      is CialloValue.FloatValue -> resolved.value.toInt()
      else -> error(what + " expects an Int, got " + resolved.format())
    }

  fun float(value: CialloValue, what: String): Float =
    when (val resolved = resolve(value)) {
      is CialloValue.IntValue -> resolved.value.toFloat()
      is CialloValue.FloatValue -> resolved.value
      else -> error(what + " expects a number, got " + resolved.format())
    }

  fun bool(value: CialloValue, what: String): Boolean =
    when (val resolved = resolve(value)) {
      is CialloValue.Bool -> resolved.value
      CialloValue.Nil -> false
      is CialloValue.IntValue -> resolved.value != 0
      is CialloValue.FloatValue -> resolved.value != 0f
      is CialloValue.Str -> resolved.value.isNotEmpty()
      else -> error(what + " expects a Bool/truthy value, got " + resolved.format())
    }

  fun block(value: CialloValue, what: String): CialloValue.BlockValue =
    resolve(value) as? CialloValue.BlockValue ?: error(what + " expects a block")

  fun error(message: String): Nothing = throw CialloRuntimeException(message)
}

private sealed interface SExpr

private data class SLiteral(val ctx: CialloParser.LiteralContext) : SExpr

private data class SSymbolLiteral(val path: List<String>) : SExpr

private data class SBlockLiteral(val value: CialloValue.BlockValue) : SExpr

private data class SEval(val ctx: CialloParser.ExpressionContext) : SExpr

private data class SMember(val receiver: SExpr?, val path: List<String>) : SExpr

private data class SCall(val receiver: SExpr?, val path: List<String>, val args: List<SExpr>) : SExpr

/**
 * Tree-walking interpreter.  It extends the generated ANTLR visitor and turns
 * each flat segment list into a small call tree before evaluating it.
 */
class CialloInterpreter(
  val environment: CialloEnvironment,
  val globalScope: CialloScope,
) : CialloBaseVisitor<CialloValue>() {
  var currentScope: CialloScope = globalScope
    private set

  private val variableOps = setOf("local", "mount", "set")

  override fun defaultResult(): CialloValue = CialloValue.Nil

  fun execute(program: CialloParser.ProgramContext): CialloValue = visitProgram(program)

  override fun visitProgram(ctx: CialloParser.ProgramContext): CialloValue {
    var result: CialloValue = CialloValue.Nil
    for (statement in ctx.statement()) {
      result = visitStatement(statement)
    }
    return result
  }

  override fun visitStatement(ctx: CialloParser.StatementContext): CialloValue {
    val expression = ctx.expression()
    val chain = singleChain(expression)
    if (chain != null) {
      val segments = chain.segment()
      if (segments.size == 1 && segments[0] is CialloParser.BlockSegmentContext) {
        val block = visitBlock((segments[0] as CialloParser.BlockSegmentContext).block())
        return executeBlock((block as CialloValue.BlockValue).block)
      }
    }
    return evalExpression(expression, asStatement = true)
  }

  override fun visitExpression(ctx: CialloParser.ExpressionContext): CialloValue =
    evalExpression(ctx, asStatement = false)

  override fun visitBlock(ctx: CialloParser.BlockContext): CialloValue =
    CialloValue.BlockValue(CialloBlock(ctx.statement(), currentScope))

  override fun visitArray(ctx: CialloParser.ArrayContext): CialloValue =
    CialloValue.Arr(ctx.expression().map { evalExpression(it, asStatement = false) }.toMutableList())

  override fun visitLiteral(ctx: CialloParser.LiteralContext): CialloValue {
    ctx.INT()?.let { return CialloValue.IntValue(it.symbol.text!!.toInt()) }
    ctx.FLOAT()?.let { return CialloValue.FloatValue(it.symbol.text!!.toFloat()) }
    ctx.NUM_SUFFIX()?.let { return parseNumberSuffix(it.symbol.text!!) }
    ctx.LONG_STRING()?.let {
      val text = it.symbol.text!!
      return CialloValue.Str(renderString(text.substring(3, text.length - 3), ctx))
    }
    ctx.STRING()?.let {
      val text = it.symbol.text!!
      return CialloValue.Str(renderString(text.substring(1, text.length - 1), ctx))
    }
    if (ctx.NULL() != null) return CialloValue.Nil
    if (ctx.TRUE() != null) return CialloValue.Bool(true)
    if (ctx.FALSE() != null) return CialloValue.Bool(false)
    ctx.array()?.let { return visitArray(it) }
    throw CialloRuntimeException("invalid literal", lineOf(ctx), columnOf(ctx))
  }

  private fun evalExpression(ctx: CialloParser.ExpressionContext, asStatement: Boolean): CialloValue {
    val chain = singleChain(ctx)
    return if (chain != null) {
      evalChain(chain, asStatement)
    } else {
      evalComparison(ctx.comparison())
    }
  }

  private fun singleChain(ctx: CialloParser.ExpressionContext): CialloParser.ChainContext? {
    val comparison = ctx.comparison()
    if (comparison.comparisonOp().isNotEmpty()) return null

    val additive = comparison.additive()
    if (additive.size != 1 || additive[0].addOp().isNotEmpty()) return null

    val multiplicative = additive[0].multiplicative()
    if (multiplicative.size != 1 || multiplicative[0].mulOp().isNotEmpty()) return null

    val unary = multiplicative[0].unary()
    if (unary.size != 1) return null
    val atom = unary[0] as? CialloParser.AtomUnaryContext ?: return null
    val primary = atom.primary()
    if (primary.expression() != null) return null
    return primary.chain()
  }

  private fun evalComparison(ctx: CialloParser.ComparisonContext): CialloValue {
    val operands = ctx.additive()
    if (operands.isEmpty()) {
      throw CialloRuntimeException("empty comparison", lineOf(ctx), columnOf(ctx))
    }
    var result = evalAdditive(operands[0])
    for (index in 1 until operands.size) {
      val operator = ctx.comparisonOp(index - 1)?.text
        ?: throw CialloRuntimeException("invalid comparison", lineOf(ctx), columnOf(ctx))
      result = applyBinary(operator, result, evalAdditive(operands[index]))
    }
    return result
  }

  private fun evalAdditive(ctx: CialloParser.AdditiveContext): CialloValue {
    val operands = ctx.multiplicative()
    if (operands.isEmpty()) {
      throw CialloRuntimeException("empty additive expression", lineOf(ctx), columnOf(ctx))
    }
    var result = evalMultiplicative(operands[0])
    for (index in 1 until operands.size) {
      val operator = ctx.addOp(index - 1)?.text
        ?: throw CialloRuntimeException("invalid additive expression", lineOf(ctx), columnOf(ctx))
      result = applyBinary(operator, result, evalMultiplicative(operands[index]))
    }
    return result
  }

  private fun evalMultiplicative(ctx: CialloParser.MultiplicativeContext): CialloValue {
    val operands = ctx.unary()
    if (operands.isEmpty()) {
      throw CialloRuntimeException("empty multiplicative expression", lineOf(ctx), columnOf(ctx))
    }
    var result = evalUnary(operands[0])
    for (index in 1 until operands.size) {
      val operator = ctx.mulOp(index - 1)?.text
        ?: throw CialloRuntimeException("invalid multiplicative expression", lineOf(ctx), columnOf(ctx))
      result = applyBinary(operator, result, evalUnary(operands[index]))
    }
    return result
  }

  private fun evalUnary(ctx: CialloParser.UnaryContext): CialloValue =
    when (ctx) {
      is CialloParser.NegateUnaryContext -> negate(evalUnary(ctx.unary()))
      is CialloParser.NotUnaryContext -> CialloValue.Bool(!truthy(evalUnary(ctx.unary())))
      is CialloParser.AtomUnaryContext -> evalPrimary(ctx.primary())
      else -> throw CialloRuntimeException("invalid unary expression", lineOf(ctx), columnOf(ctx))
    }

  private fun evalPrimary(ctx: CialloParser.PrimaryContext): CialloValue {
    val expression = ctx.expression()
    if (expression != null) {
      return evalExpression(expression, asStatement = false)
    }
    val chain = ctx.chain()
      ?: throw CialloRuntimeException("invalid primary expression", lineOf(ctx), columnOf(ctx))
    return evalChain(chain, asStatement = false)
  }

  private fun evalChain(ctx: CialloParser.ChainContext, asStatement: Boolean): CialloValue {
    val segments = ctx.segment()
    if (segments.isEmpty()) {
      throw CialloRuntimeException("empty expression", lineOf(ctx), columnOf(ctx))
    }

    var current: SExpr? = null
    for (segment in segments) {
      when (segment) {
        is CialloParser.LiteralSegmentContext -> {
          current = appendValue(current, SLiteral(segment.literal()), segment)
        }

        is CialloParser.SymbolSegmentContext -> {
          val path = splitPath(segment.namePath().text)
          if (segment.args() == null && segment.block() == null) {
            current = appendValue(current, SSymbolLiteral(path), segment)
          } else {
            if (path.size < 2) {
              throw CialloRuntimeException(
                "a symbol call needs a receiver, write @receiver.method(...)",
                lineOf(segment),
                columnOf(segment),
              )
            }
            val callArgs = mutableListOf<SExpr>()
            segment.args()?.let { callArgs += buildArgs(it) }
            segment.block()?.let { callArgs += SBlockLiteral(visitBlock(it) as CialloValue.BlockValue) }
            val call = SCall(SSymbolLiteral(path.dropLast(1)), listOf(path.last()), callArgs)
            current = appendValue(current, call, segment)
          }
        }

        is CialloParser.CallSegmentContext -> {
          val path = splitPath(segment.namePath().text)
          val args = mutableListOf<SExpr>()
          segment.args()?.let { args += buildArgs(it) }
          segment.block()?.let { args += SBlockLiteral(visitBlock(it) as CialloValue.BlockValue) }
          current =
            if (args.isEmpty()) {
              SMember(current, path)
            } else {
              SCall(current, path, args)
            }
        }

        is CialloParser.BlockSegmentContext -> {
          val block = visitBlock(segment.block()) as CialloValue.BlockValue
          current = appendValue(current, SBlockLiteral(block), segment)
        }
      }
    }

    val result = current ?: throw CialloRuntimeException("empty expression", lineOf(ctx), columnOf(ctx))
    if (asStatement && result is SSymbolLiteral) {
      throw CialloRuntimeException(
        "a name literal cannot form a statement by itself",
        lineOf(ctx),
        columnOf(ctx),
      )
    }
    return evalSExpr(result)
  }


  private fun appendValue(current: SExpr?, value: SExpr, ctx: ParserRuleContext): SExpr =
    when (current) {
      null -> value
      is SMember -> SCall(current.receiver, current.path, listOf(value))
      else ->
        throw CialloRuntimeException(
          "unexpected value after a completed call; separate statements with a newline",
          lineOf(ctx),
          columnOf(ctx),
        )
    }

  private fun buildArgs(ctx: CialloParser.ArgsContext): List<SExpr> =
    ctx.expression().map { SEval(it) }

  private fun evalSExpr(expression: SExpr): CialloValue =
    when (expression) {
      is SLiteral -> visitLiteral(expression.ctx)
      is SSymbolLiteral -> CialloValue.Symbol(expression.path)
      is SBlockLiteral -> expression.value
      is SEval -> evalExpression(expression.ctx, asStatement = false)
      is SMember -> evalMember(expression)
      is SCall -> evalCall(expression)
    }

  private fun evalMember(expression: SMember): CialloValue {
    if (expression.receiver == null) {
      if (expression.path.size == 1) return resolveName(expression.path[0])
      var value = resolveName(expression.path[0])
      for (index in 1 until expression.path.size) {
        value = getMember(value, expression.path[index])
      }
      return value
    }

    var value = evalSExpr(expression.receiver)
    for (part in expression.path) {
      value = getMember(value, part)
    }
    return value
  }

  private fun evalCall(expression: SCall): CialloValue {
    val args = expression.args.map { evalSExpr(it) }
    if (expression.receiver == null) {
      if (expression.path.size == 1) {
        return callFunction(expression.path[0], args)
      }
      var value = resolveName(expression.path[0])
      for (index in 1 until expression.path.size - 1) {
        value = getMember(value, expression.path[index])
      }
      return callMember(value, expression.path.last(), args)
    }

    var value = evalSExpr(expression.receiver)
    for (index in 0 until expression.path.size - 1) {
      value = getMember(value, expression.path[index])
    }
    return callMember(value, expression.path.last(), args)
  }

  fun resolveForCall(value: CialloValue): CialloValue =
    if (value is CialloValue.Symbol) resolveSymbol(value) else value

  fun resolveName(name: String): CialloValue {
    currentScope.lookup(name)?.let { return it }
    environment.natives[name]?.let { return CialloValue.NativeValue(name, it) }
    environment.symbols[name]?.let { return it }
    throw CialloRuntimeException("unknown name '" + name + "'")
  }

  fun resolveSymbol(symbol: CialloValue.Symbol): CialloValue {
    val path = symbol.path
    if (path.isEmpty()) throw CialloRuntimeException("empty name literal")

    if (path.size == 1) {
      currentScope.lookup(path[0])?.let { return it }
      environment.symbols[path[0]]?.let { return it }
      throw CialloRuntimeException("unknown name '" + path[0] + "'")
    }

    val scoped = currentScope.lookup(path[0])
    if (scoped != null) {
      var value: CialloValue = scoped
      for (index in 1 until path.size) {
        value = getMember(value, path[index])
      }
      return value
    }

    environment.symbols[symbol.name]?.let { return it }

    val hosted = environment.symbols[path[0]]
    if (hosted != null) {
      var value: CialloValue = hosted
      for (index in 1 until path.size) {
        value = getMember(value, path[index])
      }
      return value
    }

    if (environment.sandbox.allowJvmAccess) {
      CialloJavaAccess.resolveStatic(environment.sandbox, path)?.let { return it }
    }

    throw CialloRuntimeException("unknown name '" + symbol.name + "'")
  }

  fun callFunction(name: String, args: List<CialloValue>): CialloValue {
    val local = currentScope.lookup(name)
    if (local != null) {
      val native = local as? CialloValue.NativeValue
        ?: throw CialloRuntimeException("'" + name + "' is not a function")
      return native.native.invoke(context(), args)
    }
    val native = environment.natives[name]
      ?: throw CialloRuntimeException("unknown function '" + name + "'")
    return native.invoke(context(), args)
  }

  fun callMember(receiver: CialloValue, name: String, args: List<CialloValue>): CialloValue {
    if (receiver is CialloValue.Symbol && name in variableOps) {
      return variableOperation(receiver, name, args)
    }

    val target = resolveForCall(receiver)
    return when (target) {
      is CialloValue.IntValue, is CialloValue.FloatValue -> numericMethod(target, name, args)
      is CialloValue.Str -> stringMethod(target, name, args)
      is CialloValue.Arr -> arrayMethod(target, name, args)
      is CialloValue.Bool -> boolMethod(target, name, args)
      is CialloValue.TimeValue -> timeMethod(target, name, args)
      is CialloValue.Obj -> objectMethod(target, name, args)
      is CialloValue.BlockValue -> blockMethod(target, name, args)
      is CialloValue.JavaValue -> {
        if (!environment.sandbox.allowJvmAccess) {
          throw CialloSandboxException("method '" + name + "' on a JVM object")
        }
        if (name == "apply") {
          requireArgs(name, args, 1)
          val block = resolveForCall(args[0]) as? CialloValue.BlockValue
            ?: throw CialloRuntimeException("obj.apply expects a block")
          val scope = block.block.closure.child(CialloJavaScopeBacking(target.value, environment.sandbox))
          executeBlockInScope(block.block, scope)
          target
        } else {
          CialloJavaAccess.callMethod(target.value, name, args.map { resolveForCall(it) }, environment.sandbox)
        }
      }
      is CialloValue.NativeValue -> throw CialloRuntimeException("cannot call method '" + name + "' on a native function")
      is CialloValue.Symbol -> throw CialloRuntimeException("unknown symbol member '" + name + "'")
      CialloValue.Nil -> nilMethod(target, name, args)
    }
  }

  fun getMember(value: CialloValue, name: String): CialloValue {
    val target = resolveForCall(value)
    return when (target) {
      is CialloValue.Obj -> target.fields[name]
        ?: throw CialloRuntimeException("object has no field '" + name + "'")
      is CialloValue.Arr ->
        when (name) {
          "size", "length" -> CialloValue.IntValue(target.items.size)
          else -> throw CialloRuntimeException("array has no field '" + name + "'")
        }
      is CialloValue.Str ->
        when (name) {
          "size", "length" -> CialloValue.IntValue(target.value.length)
          else -> throw CialloRuntimeException("string has no field '" + name + "'")
        }
      is CialloValue.TimeValue ->
        when (name) {
          "millis" -> CialloValue.IntValue(target.millis.toInt())
          "seconds" -> CialloValue.FloatValue((target.millis / 1000.0).toFloat())
          "amount" -> CialloValue.FloatValue(target.amount.toFloat())
          else -> throw CialloRuntimeException("time has no field '" + name + "'")
        }
      is CialloValue.JavaValue -> {
        if (!environment.sandbox.allowJvmAccess) {
          throw CialloSandboxException("field '" + name + "' on a JVM object")
        }
        CialloJavaAccess.getField(target.value, name, environment.sandbox)
      }
      is CialloValue.Symbol -> throw CialloRuntimeException("unknown symbol field '" + name + "'")
      is CialloValue.NativeValue -> throw CialloRuntimeException("native function has no field '" + name + "'")
      is CialloValue.BlockValue -> throw CialloRuntimeException("block has no field '" + name + "'")
      is CialloValue.IntValue, is CialloValue.FloatValue -> throw CialloRuntimeException("number has no field '" + name + "'")
      is CialloValue.Bool -> throw CialloRuntimeException("bool has no field '" + name + "'")
      CialloValue.Nil -> throw CialloRuntimeException("null has no field '" + name + "'")
    }
  }

  fun executeBlock(block: CialloBlock, bindings: Map<String, CialloValue> = emptyMap()): CialloValue {
    val scope = block.closure.child()
    scope.defineAll(bindings)
    return executeBlockInScope(block, scope)
  }

  fun executeBlockInScope(block: CialloBlock, scope: CialloScope): CialloValue {
    val previous = currentScope
    currentScope = scope
    return try {
      var result: CialloValue = CialloValue.Nil
      for (statement in block.statements) {
        result = visitStatement(statement)
      }
      result
    } finally {
      currentScope = previous
    }
  }

  private fun context(): CialloCallContext = CialloCallContext(this, environment, currentScope)

  private fun variableOperation(symbol: CialloValue.Symbol, operation: String, args: List<CialloValue>): CialloValue {
    if (args.size != 1) {
      throw CialloRuntimeException("'" + operation + "' expects exactly one argument")
    }
    val value = resolveForCall(args[0])
    val path = symbol.path
    if (operation == "set" && path.size > 1) {
      var parent = resolveSymbol(CialloValue.Symbol(path.dropLast(1)))
      setMember(parent, path.last(), value)
      return value
    }

    if (path.size != 1) {
      throw CialloRuntimeException("'" + operation + "' needs a simple name, got @" + symbol.name)
    }

    when (operation) {
      "local" -> currentScope.defineLocal(path[0], value)
      "mount" -> currentScope.defineGlobal(path[0], value)
      "set" -> currentScope.assign(path[0], value)
    }
    return value
  }

  private fun setMember(receiver: CialloValue, name: String, value: CialloValue) {
    val target = resolveForCall(receiver)
    when (target) {
      is CialloValue.Obj -> target.fields[name] = value
      is CialloValue.JavaValue -> {
        if (!environment.sandbox.allowJvmAccess) {
          throw CialloSandboxException("field '" + name + "' on a JVM object")
        }
        CialloJavaAccess.setField(target.value, name, value, environment.sandbox)
      }
      else -> throw CialloRuntimeException("cannot assign field '" + name + "' on " + target.format())
    }
  }

  private fun numericMethod(target: CialloValue, name: String, args: List<CialloValue>): CialloValue {
    requireArgs(name, args, 1)
    val right = resolveForCall(args[0])
    return when (name) {
      "add", "plus" -> numericBinary(target, right, "+")
      "sub", "minus" -> numericBinary(target, right, "-")
      "mul", "times" -> numericBinary(target, right, "*")
      "div" -> numericBinary(target, right, "/")
      "mod" -> numericBinary(target, right, "%")
      "less" -> numericCompare(target, right, "<")
      "lessEq" -> numericCompare(target, right, "<=")
      "greater" -> numericCompare(target, right, ">")
      "greaterEq" -> numericCompare(target, right, ">=")
      "eq" -> CialloValue.Bool(asNumber(target) == asNumber(right))
      "ne" -> CialloValue.Bool(asNumber(target) != asNumber(right))
      else -> throw CialloRuntimeException("number has no method '" + name + "'")
    }
  }

  private fun numericBinary(left: CialloValue, right: CialloValue, operator: String): CialloValue {
    val leftNumber = asNumber(left)
    val rightNumber = asNumber(right)
    val result =
      when (operator) {
        "+" -> leftNumber + rightNumber
        "-" -> leftNumber - rightNumber
        "*" -> leftNumber * rightNumber
        "/" -> {
          if (rightNumber == 0.0) throw CialloRuntimeException("division by zero")
          leftNumber / rightNumber
        }
        "%" -> {
          if (rightNumber == 0.0) throw CialloRuntimeException("division by zero")
          leftNumber % rightNumber
        }
        else -> throw CialloRuntimeException("unknown numeric operator " + operator)
      }
    return if (left is CialloValue.IntValue && right is CialloValue.IntValue) {
      CialloValue.IntValue(result.toInt())
    } else {
      CialloValue.FloatValue(result.toFloat())
    }
  }

  private fun numericCompare(left: CialloValue, right: CialloValue, operator: String): CialloValue {
    val a = asNumber(left)
    val b = asNumber(right)
    val result =
      when (operator) {
        "<" -> a < b
        "<=" -> a <= b
        ">" -> a > b
        ">=" -> a >= b
        else -> false
      }
    return CialloValue.Bool(result)
  }

  private fun asNumber(value: CialloValue): Double =
    when (value) {
      is CialloValue.IntValue -> value.value.toDouble()
      is CialloValue.FloatValue -> value.value.toDouble()
      else -> throw CialloRuntimeException("expected a number, got " + value.format())
    }

  private fun applyBinary(operator: String, left: CialloValue, right: CialloValue): CialloValue =
    when (operator) {
      "+" -> addValues(left, right)
      "-" -> subValues(left, right)
      "*" -> mulValues(left, right)
      "/" -> divValues(left, right)
      "%" -> numericBinary(resolveForCall(left), resolveForCall(right), "%")
      "==" -> CialloValue.Bool(equalValues(left, right))
      "!=" -> CialloValue.Bool(!equalValues(left, right))
      "<" -> compareValues(left, right) { a, b -> a < b }
      "<=" -> compareValues(left, right) { a, b -> a <= b }
      ">" -> compareValues(left, right) { a, b -> a > b }
      ">=" -> compareValues(left, right) { a, b -> a >= b }
      else -> throw CialloRuntimeException("unknown operator '" + operator + "'")
    }

  private fun equalValues(left: CialloValue, right: CialloValue): Boolean {
    val first = resolveForCall(left)
    val second = resolveForCall(right)
    if (isNumber(first) && isNumber(second)) {
      return asNumber(first) == asNumber(second)
    }
    return first == second
  }

  private fun compareValues(
    left: CialloValue,
    right: CialloValue,
    predicate: (Double, Double) -> Boolean,
  ): CialloValue {
    val first = resolveForCall(left)
    val second = resolveForCall(right)
    if (!isNumber(first) || !isNumber(second)) {
      throw CialloRuntimeException(
        "comparison expects numbers, got " + first.format() + " and " + second.format(),
      )
    }
    return CialloValue.Bool(predicate(asNumber(first), asNumber(second)))
  }

  private fun isNumber(value: CialloValue): Boolean =
    value is CialloValue.IntValue || value is CialloValue.FloatValue

  private fun truthy(value: CialloValue): Boolean =
    when (val resolved = resolveForCall(value)) {
      CialloValue.Nil -> false
      is CialloValue.Bool -> resolved.value
      is CialloValue.IntValue -> resolved.value != 0
      is CialloValue.FloatValue -> resolved.value != 0f
      is CialloValue.Str -> resolved.value.isNotEmpty()
      else -> true
    }

  private fun negate(value: CialloValue): CialloValue =
    when (val resolved = resolveForCall(value)) {
      is CialloValue.IntValue -> CialloValue.IntValue(-resolved.value)
      is CialloValue.FloatValue -> CialloValue.FloatValue(-resolved.value)
      else -> throw CialloRuntimeException("unary '-' expects a number, got " + resolved.format())
    }

  private fun stringMethod(target: CialloValue.Str, name: String, args: List<CialloValue>): CialloValue =
    when (name) {
      "add", "plus" -> {
        requireArgs(name, args, 1)
        CialloValue.Str(target.value + resolveForCall(args[0]).format())
      }
      "upper" -> CialloValue.Str(target.value.uppercase())
      "lower" -> CialloValue.Str(target.value.lowercase())
      "size", "length" -> CialloValue.IntValue(target.value.length)
      "toString" -> target
      else -> throw CialloRuntimeException("string has no method '" + name + "'")
    }

  private fun arrayMethod(target: CialloValue.Arr, name: String, args: List<CialloValue>): CialloValue =
    when (name) {
      "size", "length" -> CialloValue.IntValue(target.items.size)
      "add" -> {
        requireArgs(name, args, 1)
        target.items += args[0]
        target
      }
      "get" -> {
        requireArgs(name, args, 1)
        val index = toIndex(args[0], target.items.size)
        target.items[index]
      }
      "set" -> {
        requireArgs(name, args, 2)
        val index = toIndex(args[0], target.items.size)
        target.items[index] = args[1]
        target
      }
      "join" -> {
        val separator = if (args.isEmpty()) "" else resolveForCall(args[0]).format()
        CialloValue.Str(target.items.joinToString(separator) { resolveForCall(it).format() })
      }
      "toString" -> CialloValue.Str(target.format())
      else -> throw CialloRuntimeException("array has no method '" + name + "'")
    }

  private fun toIndex(value: CialloValue, size: Int): Int {
    val index = (resolveForCall(value) as? CialloValue.IntValue)?.value
      ?: throw CialloRuntimeException("array index must be an Int")
    if (index !in 0 until size) throw CialloRuntimeException("array index " + index + " is out of bounds")
    return index
  }

  private fun boolMethod(target: CialloValue.Bool, name: String, args: List<CialloValue>): CialloValue =
    when (name) {
      "not" -> CialloValue.Bool(!target.value)
      "toString" -> CialloValue.Str(target.format())
      else -> throw CialloRuntimeException("bool has no method '" + name + "'")
    }

  private fun timeMethod(target: CialloValue.TimeValue, name: String, args: List<CialloValue>): CialloValue =
    when (name) {
      "toMillis" -> CialloValue.IntValue(target.millis.toInt())
      "toSeconds" -> CialloValue.FloatValue((target.millis / 1000.0).toFloat())
      "toString" -> CialloValue.Str(target.format())
      "add" -> {
        requireArgs(name, args, 1)
        val other = resolveForCall(args[0]) as? CialloValue.TimeValue
          ?: throw CialloRuntimeException("time.add expects another Time")
        CialloValue.TimeValue(target.amount + other.amount, target.unit)
      }
      else -> throw CialloRuntimeException("time has no method '" + name + "'")
    }

  private fun objectMethod(target: CialloValue.Obj, name: String, args: List<CialloValue>): CialloValue {
    if (name == "apply") {
      requireArgs(name, args, 1)
      val block = resolveForCall(args[0]) as? CialloValue.BlockValue
        ?: throw CialloRuntimeException("obj.apply expects a block")
      val scope = block.block.closure.child(CialloObjScopeBacking(target))
      executeBlockInScope(block.block, scope)
      return target
    }

    val field = target.fields[name]
    if (field != null) {
      if (field is CialloValue.NativeValue) return field.native.invoke(context(), args)
      if (args.isEmpty()) return field
      throw CialloRuntimeException("field '" + name + "' is not a function")
    }
    throw CialloRuntimeException("object has no method '" + name + "'")
  }

  private fun blockMethod(target: CialloValue.BlockValue, name: String, args: List<CialloValue>): CialloValue =
    when (name) {
      "run", "then" -> {
        val bindings =
          when {
            args.isEmpty() -> emptyMap()
            else -> mapOf("it" to args[0])
          }
        executeBlock(target.block, bindings)
      }
      else -> throw CialloRuntimeException("block has no method '" + name + "'")
    }

  private fun nilMethod(target: CialloValue, name: String, args: List<CialloValue>): CialloValue =
    when (name) {
      "toString" -> CialloValue.Str("null")
      "isNil" -> CialloValue.Bool(true)
      else -> throw CialloRuntimeException("null has no method '" + name + "'")
    }

  private fun requireArgs(name: String, args: List<CialloValue>, count: Int) {
    if (args.size != count) {
      throw CialloRuntimeException("'" + name + "' expects " + count + " argument(s), got " + args.size)
    }
  }

  fun addValues(left: CialloValue, right: CialloValue): CialloValue {
    val a = resolveForCall(left)
    val b = resolveForCall(right)
    if (a is CialloValue.Str) return CialloValue.Str(a.value + b.format())
    return numericBinary(a, b, "+")
  }

  fun subValues(left: CialloValue, right: CialloValue): CialloValue =
    numericBinary(resolveForCall(left), resolveForCall(right), "-")

  fun mulValues(left: CialloValue, right: CialloValue): CialloValue =
    numericBinary(resolveForCall(left), resolveForCall(right), "*")

  fun divValues(left: CialloValue, right: CialloValue): CialloValue =
    numericBinary(resolveForCall(left), resolveForCall(right), "/")

  fun interface NumberSuffixParser{
    fun parse(text: String): CialloValue?
  }

  val numberSuffixParsers = mutableListOf<NumberSuffixParser>(NumberSuffixParser { parseTime(it) })

  fun parseNumberSuffix(text: String): CialloValue =
    when (text.last()) {
      'f', 'F' -> CialloValue.FloatValue(text.removeSuffix("f").removeSuffix("F").toFloat())
      else -> numberSuffixParsers.firstNotNullOfOrNull { it.parse(text) } ?: 
        throw CialloRuntimeException("invalid number literal '" + text + "'")
    }

  private fun parseTime(text: String): CialloValue.TimeValue? {
    val unit =
      when (text.last()) {
        's' -> CialloTimeUnit.Second
        'm' -> CialloTimeUnit.Minute
        'h' -> CialloTimeUnit.Hour
        else -> return null
      }
    val amount = text.dropLast(1).toDouble()
    return CialloValue.TimeValue(amount, unit)
  }

  private fun renderString(text: String, ctx: ParserRuleContext): String {
    val builder = StringBuilder()
    var index = 0
    while (index < text.length) {
      val char = text[index]
      when {
        char == '\\' && index + 1 < text.length -> {
          val (value, next) = unescape(text, index)
          builder.append(value)
          index = next
        }
        char == '{' -> {
          val end = findInterpolationEnd(text, index)
          if (end < 0) {
            throw CialloRuntimeException(
              "unterminated string interpolation",
              lineOf(ctx),
              columnOf(ctx),
            )
          }
          val expression = text.substring(index + 1, end).trim()
          if (expression.isEmpty()) {
            throw CialloRuntimeException("empty string interpolation", lineOf(ctx), columnOf(ctx))
          }
          val value = evalExpression(parseInterpolation(expression, ctx), asStatement = false)
          builder.append(resolveForCall(value).format())
          index = end + 1
        }
        else -> {
          builder.append(char)
          index++
        }
      }
    }
    return builder.toString()
  }

  private fun parseInterpolation(source: String, ctx: ParserRuleContext): CialloParser.ExpressionContext {
    val lexer = CialloLexer(CharStreams.fromString(source, "<interpolation>"))
    lexer.removeErrorListeners()
    lexer.addErrorListener(CialloErrorListener)
    val parser = CialloParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    parser.addErrorListener(CialloErrorListener)
    val program = parser.program()
    val statement = program.statement().firstOrNull()
      ?: throw CialloRuntimeException("empty string interpolation", lineOf(ctx), columnOf(ctx))
    return statement.expression()
  }

  private fun findInterpolationEnd(text: String, start: Int): Int {
    var depth = 1
    var index = start + 1
    var inString = false
    var escaped = false
    while (index < text.length) {
      val char = text[index]
      if (inString) {
        when {
          escaped -> escaped = false
          char == '\\' -> escaped = true
          char == '"' -> inString = false
        }
      } else {
        when (char) {
          '"' -> inString = true
          '{' -> depth++
          '}' -> {
            depth--
            if (depth == 0) return index
          }
        }
      }
      index++
    }
    return -1
  }

  private fun unescape(text: String, index: Int): Pair<String, Int> {
    val escaped = text[index + 1]
    return when (escaped) {
      'n' -> "\n" to index + 2
      'r' -> "\r" to index + 2
      't' -> "\t" to index + 2
      'b' -> "\b" to index + 2
      'f' -> "\u000C" to index + 2
      '"' -> "\"" to index + 2
      '\'' -> "'" to index + 2
      '\\' -> "\\" to index + 2
      '{' -> "{" to index + 2
      '}' -> "}" to index + 2
      'u' -> {
        if (index + 6 > text.length) throw CialloRuntimeException("invalid unicode escape")
        val hex = text.substring(index + 2, index + 6)
        val code = hex.toInt(16)
        code.toChar().toString() to index + 6
      }
      else -> escaped.toString() to index + 2
    }
  }

  private fun splitPath(text: String): List<String> = text.split('.')

  private fun lineOf(ctx: ParserRuleContext): Int = ctx.start?.line ?: -1

  private fun columnOf(ctx: ParserRuleContext): Int = ctx.start?.charPositionInLine ?: -1
}
