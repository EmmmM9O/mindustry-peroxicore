package peroxicore.ciallo

import org.antlr.v4.kotlinruntime.*
import peroxicore.ciallo.ops.*
import peroxicore.ciallo.value.*
import peroxicore.parsers.*

private sealed interface SExpr

private data class SLiteral(val ctx: CialloParser.LiteralContext) : SExpr

private data class SSymbolLiteral(val path: List<String>) : SExpr

private data class SBlockLiteral(val value: CialloBlockValue) : SExpr

private data class SEval(val ctx: CialloParser.ExpressionContext) : SExpr

private data class SMember(val receiver: SExpr?, val path: List<String>) : SExpr

private data class SCall(val receiver: SExpr?, val path: List<String>, val args: List<SExpr>) : SExpr

/**
 * Tree-walking interpreter.  It extends the generated ANTLR visitor and turns
 * each flat segment list into a small call tree before evaluating it.
 *
 * The interpreter owns the syntax: expressions, statements, scopes and the call
 * tree.  The semantics of values live in [CialloValue.members] (per value type)
 * and in [CialloOperations] (operators, free functions, name resolution), so
 * host code extends the language without touching this class.
 */
class CialloInterpreter(
  val environment: CialloEnvironment,
  val globalScope: CialloScope,
) : CialloBaseVisitor<CialloValue>() {
  var currentScope: CialloScope = globalScope
    private set

  val operations: CialloOperations get() = environment.operations

  private val variableOps = setOf("local", "mount", "set")

  override fun defaultResult(): CialloValue = CialloNil.INSTANCE

  fun execute(program: CialloParser.ProgramContext): CialloValue = visitProgram(program)

  fun context(): CialloCallContext = CialloCallContext(this, environment, currentScope)

  override fun visitProgram(ctx: CialloParser.ProgramContext): CialloValue {
    var result: CialloValue = CialloNil.INSTANCE
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
        val block = visitBlock((segments[0] as CialloParser.BlockSegmentContext).block()) as CialloBlockValue
        return executeBlock(block.block)
      }
    }
    return evalExpression(expression, asStatement = true)
  }

  override fun visitExpression(ctx: CialloParser.ExpressionContext): CialloValue =
    evalExpression(ctx, asStatement = false)

  override fun visitBlock(ctx: CialloParser.BlockContext): CialloValue =
    CialloBlockValue(CialloBlock(ctx.statement(), currentScope))

  override fun visitArray(ctx: CialloParser.ArrayContext): CialloValue =
    CialloArr(ctx.expression().map { evalExpression(it, asStatement = false) }.toMutableList())

  override fun visitLiteral(ctx: CialloParser.LiteralContext): CialloValue {
    ctx.INT()?.let { return CialloInt(it.symbol.text!!.toInt()) }
    ctx.FLOAT()?.let { return CialloFloat(it.symbol.text!!.toFloat()) }
    ctx.NUM_SUFFIX()?.let { return parseNumberSuffix(it.symbol.text!!) }
    ctx.LONG_STRING()?.let {
      val text = it.symbol.text!!
      return CialloStr(renderString(text.substring(3, text.length - 3), ctx))
    }
    ctx.STRING()?.let {
      val text = it.symbol.text!!
      return CialloStr(renderString(text.substring(1, text.length - 1), ctx))
    }
    if (ctx.NULL() != null) return CialloNil.INSTANCE
    if (ctx.TRUE() != null) return CialloBool(true)
    if (ctx.FALSE() != null) return CialloBool(false)
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
      result = evaluateBinary(operator, result, evalAdditive(operands[index]))
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
      result = evaluateBinary(operator, result, evalMultiplicative(operands[index]))
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
      result = evaluateBinary(operator, result, evalUnary(operands[index]))
    }
    return result
  }

  private fun evalUnary(ctx: CialloParser.UnaryContext): CialloValue =
    when (ctx) {
      is CialloParser.NegateUnaryContext -> evaluateOperator("-", evalUnary(ctx.unary()))
      is CialloParser.NotUnaryContext -> evaluateOperator("!", evalUnary(ctx.unary()))
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
            segment.block()?.let { callArgs += SBlockLiteral(visitBlock(it) as CialloBlockValue) }
            val call = SCall(SSymbolLiteral(path.dropLast(1)), listOf(path.last()), callArgs)
            current = appendValue(current, call, segment)
          }
        }

        is CialloParser.CallSegmentContext -> {
          val path = splitPath(segment.namePath().text)
          val args = mutableListOf<SExpr>()
          segment.args()?.let { args += buildArgs(it) }
          segment.block()?.let { args += SBlockLiteral(visitBlock(it) as CialloBlockValue) }
          current =
            if (segment.args() == null && segment.block() == null) {
              SMember(current, path)
            } else {
              SCall(current, path, args)
            }
        }

        is CialloParser.MemberSegmentContext -> {
          // `.name` continues whatever the previous segment produced: `at(5, 5).showConfig()`
          // is the same chain as `at(5, 5) showConfig()`.
          val receiver =
            current
              ?: throw CialloRuntimeException(
                "a member call needs a receiver, write value.name(...)",
                lineOf(segment),
                columnOf(segment),
              )
          val path = splitPath(segment.namePath().text)
          val args = mutableListOf<SExpr>()
          segment.args()?.let { args += buildArgs(it) }
          segment.block()?.let { args += SBlockLiteral(visitBlock(it) as CialloBlockValue) }
          current =
            if (segment.args() == null && segment.block() == null) {
              SMember(receiver, path)
            } else {
              SCall(receiver, path, args)
            }
        }

        is CialloParser.BlockSegmentContext -> {
          val block = visitBlock(segment.block()) as CialloBlockValue
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
      is SSymbolLiteral -> CialloSymbol(expression.path)
      is SBlockLiteral -> expression.value
      is SEval -> evalExpression(expression.ctx, asStatement = false)
      is SMember -> evalMember(expression)
      is SCall -> evalCall(expression)
    }

  private fun evalMember(expression: SMember): CialloValue {
    if (expression.receiver == null) {
      val head = expression.path.first()
      if (expression.path.size == 1) return resolveName(head) ?: unknownName(head)
      return memberPath(resolveName(head) ?: unknownName(head), expression.path.drop(1))
    }
    return memberPath(evalSExpr(expression.receiver), expression.path)
  }

  private fun evalCall(expression: SCall): CialloValue {
    val args = expression.args.map { evalSExpr(it) }
    if (expression.receiver == null) {
      val name = expression.path.first()
      if (expression.path.size == 1) {
        return callFunction(name, args) ?: unknownFunction(name)
      }
      val receiver = memberPath(resolveName(name) ?: unknownName(name), expression.path.subList(1, expression.path.size - 1))
      return callMember(receiver, expression.path.last(), args)
    }
    return callMember(memberPath(evalSExpr(expression.receiver), expression.path.dropLast(1)), expression.path.last(), args)
  }

  /** The value of `receiver + name`; the `@MethodEntry` components override the value tables. */
  fun getMember(receiver: CialloValue, name: String, result: CialloValue? = null): CialloValue {
    result?.let { return it }
    val context = context()
    operations.memberHandler(receiver, name)?.call(context, receiver, name, null)?.let { return it }

    val target = memberTargetFor(receiver)
    target.members.member(context, target, name)?.let { return it }
    throw CialloRuntimeException(receiver.kind.displayName.lowercase() + " has no field '" + name + "'")
  }

  /** Calls `receiver.name(args)`. */
  fun callMember(
    receiver: CialloValue,
    name: String,
    args: List<CialloValue>,
    result: CialloValue? = null,
  ): CialloValue {
    result?.let { return it }
    // Variable operations take exactly one argument.  Any other arity means this is a real member
    // call on the symbol, so `@a.set(0, 9)` reaches the array's `set` instead of failing as a
    // variable assignment.
    if (receiver is CialloSymbol && name in variableOps && args.size == 1) {
      return CialloSymbolMembers.call(context(), receiver, name, args)
        ?: throw CialloRuntimeException("'" + name + "' is not a variable operation")
    }

    val context = context()
    operations.memberHandler(receiver, name)?.call(context, receiver, name, args)?.let { return it }

    val target = memberTargetFor(receiver)
    target.members.call(context, target, name, args)?.let { return it }
    // A table that declares strict overloads for the name but matched none gets the precise
    // diagnostic instead of a generic "has no method".
    val signatures = target.memberTableView()?.parameterLists(name).orEmpty()
    if (signatures.isNotEmpty()) {
      throw CialloRuntimeException(
        noOverloadMessage("method '" + name + "'", signatures, args.map { context.resolve(it) }),
      )
    }
    throw CialloRuntimeException(receiver.kind.displayName.lowercase() + " has no method '" + name + "'")
  }

  /** Reads `receiver.name` for every name in [path]. */
  fun memberPath(receiver: CialloValue, path: List<String>): CialloValue {
    var value = receiver
    for (name in path) {
      value = getMember(value, name)
    }
    return value
  }

  fun resolveForCall(value: CialloValue): CialloValue =
    if (value is CialloSymbol) resolveSymbol(value) ?: throw unknownName(value.name) else value

  /**
   * The value whose members answer a receiver.  A name literal first resolves to
   * what it names, so `@x.add(1)` behaves like `@x add(1)`.
   */
  fun memberTargetFor(receiver: CialloValue): CialloValue {
    if (receiver !is CialloSymbol) return receiver.memberTarget()
    return resolveSymbol(receiver)?.memberTarget() ?: throw unknownName(receiver.name)
  }

  /**
   * A bare identifier: a variable, then a native function, then a registered
   * symbol.  `@MethodEntry` components (see [peroxicore.ciallo.value.CialloSymbolNames])
   * answer last.
   */
  fun resolveName(name: String, result: CialloValue? = null): CialloValue? {
    result?.let { return it }
    currentScope.lookup(name)?.let { return it }
    environment.natives[name]?.let { return CialloNativeValue(name, it) }
    environment.symbols[name]?.let { return it }
    return nameResolvers().firstNotNullOfOrNull { it.resolve(this, listOf(name)) }
  }

  /** `@x`: variables and registered symbols first, then the `@MethodEntry` components. */
  fun resolveSymbol(symbol: CialloSymbol): CialloValue? {
    val path = symbol.path
    if (path.isEmpty()) throw CialloRuntimeException("empty name literal")

    currentScope.lookup(path.first())?.let { return memberPath(it, path.drop(1)) }
    environment.symbols[symbol.name]?.let { return it }
    environment.symbols[path.first()]?.let { return memberPath(it, path.drop(1)) }
    return nameResolvers().firstNotNullOfOrNull { it.resolve(this, path) }
  }

  /** The registered name resolvers, enriched by the `@MethodEntry` components. */
  fun nameResolvers(result: List<CialloNameResolver>? = null): List<CialloNameResolver> =
    result ?: operations.symbolResolvers()

  fun unknownName(name: String): Nothing =
    throw CialloRuntimeException("unknown name '" + name + "'")


  /**
   * A free function call.  A local variable named after a native shadows the
   * registered functions; otherwise the standard library components
   * (`@MethodEntry` on this method) and then the registered [CialloFreeFunction]s
   * answer.
   */
  fun callFunction(name: String, args: List<CialloValue>, result: CialloValue? = null): CialloValue? {
    result?.let { return it }
    val context = context()
    val local = currentScope.lookup(name)
    if (local != null) {
      val native = local as? CialloNativeValue
        ?: throw CialloRuntimeException("'" + name + "' is not a function")
      return native.native.invoke(context, args)
    }
    environment.natives[name]?.let { return it.invoke(context, args) }
    val free = operations.freeFunction(name) ?: return null
    free.call(context, args)?.let { return it }
    // The function exists but declined: when it declares strict overloads, report the mismatch
    // instead of the misleading "unknown function".
    val signatures = operations.freeFunctionParameterLists(name)
    if (signatures.isNotEmpty()) {
      throw CialloRuntimeException(noOverloadMessage("function '" + name + "'", signatures, args.map { context.resolve(it) }))
    }
    return null
  }

  fun unknownFunction(name: String): Nothing =
    throw CialloRuntimeException("unknown function '" + name + "'")

  /**
   * `@name set value` assigns a variable; `@obj.field set value` assigns a member.
   *
   * A name literal receiver that itself names something is assigned through, so
   * `@x set 8` both updates the variable `x` and writes member `x` of the object
   * the current scope exposes (an object entered with `obj.apply{ ... }`).
   */
  fun setMember(receiver: CialloValue, name: String, value: CialloValue): CialloValue {
    val context = context()
    if (receiver is CialloSymbol) {
      val path = receiver.path
      if (path.isEmpty()) throw CialloRuntimeException("'set' needs a name")
      if (path.size > 1) {
        val parent = resolveSymbol(CialloSymbol(path)) ?: unknownName(receiver.name)
        return setMember(parent, name, value)
      }
      val variable = path[0]
      // Inside `obj.apply{ ... }` the name may be a field of the entered object:
      // assign the field too, then update the variable the name refers to.
      if (name !in variableOps) {
        currentScope.lookup(variable)?.let { exposed ->
          if (exposed !== receiver) runCatching { setMemberValue(exposed, variable, value) }
        }
      }
      currentScope.assign(variable, value)
      return value
    }
    return setMemberValue(receiver, name, value)
  }

  private fun setMemberValue(receiver: CialloValue, name: String, value: CialloValue): CialloValue {
    val context = context()
    operations.memberHandler(receiver, name)?.call(context, receiver, name, listOf(value))?.let { return it }

    val target = memberTargetFor(receiver)
    if (target is CialloJavaValue) {
      context.environment.javaBridge.setMember(target.value, name, value, context.environment.sandbox)
      return value
    }
    if (target.setMember(context, name, value)) return value
    throw CialloRuntimeException("cannot assign field '" + name + "' on " + target.render())
  }

  /** A child scope of [block]'s closure with [bindings] defined in it. */
  fun scopeFor(block: CialloBlock, bindings: Map<String, CialloValue> = emptyMap()): CialloScope {
    val scope = block.closure.child()
    scope.defineAll(bindings)
    return scope
  }

  fun executeBlock(block: CialloBlock, bindings: Map<String, CialloValue> = emptyMap()): CialloValue =
    executeBlockInScope(block, scopeFor(block, bindings))

  fun executeBlockInScope(block: CialloBlock, scope: CialloScope): CialloValue {
    val previous = currentScope
    currentScope = scope
    return try {
      var result: CialloValue = CialloNil.INSTANCE
      for (statement in block.statements) {
        result = visitStatement(statement)
      }
      result
    } finally {
      currentScope = previous
    }
  }

  /**
   * Runs [block] in a custom scope and restores the previous scope afterwards.
   * The scope is a child of the block's closure, so the block still sees its own
   * variables; [backing] and [bindings] shadow them.
   */
  fun <R> withBlockScope(
    block: CialloBlock,
    bindings: Map<String, CialloValue> = emptyMap(),
    backing: CialloScopeBacking? = null,
    body: () -> R,
  ): R {
    val scope = block.closure.child(backing)
    scope.defineAll(bindings)
    val previous = currentScope
    currentScope = scope
    return try {
      body()
    } finally {
      currentScope = previous
    }
  }

  /** The scope `@name local v` defines into: the current one. */
  fun localScopeFor(symbol: CialloSymbol): CialloScope = requireSimpleName("local", symbol, currentScope)

  /** The scope `@name mount v` defines into: the root scope. */
  fun globalScopeOf(symbol: CialloSymbol): CialloScope = requireSimpleName("mount", symbol, globalScope)

  /** `@name set v`, and `@obj.field set v` for a path. */
  fun assignSymbol(symbol: CialloSymbol, value: CialloValue): CialloValue {
    val path = symbol.path
    if (path.size > 1) {
      val parent = resolveSymbol(CialloSymbol(path.dropLast(1))) ?: unknownName(symbol.name)
      return setMember(parent, path.last(), value)
    }
    if (path.isEmpty()) throw CialloRuntimeException("'set' needs a name")
    currentScope.assign(path[0], value)
    return value
  }

  private fun requireSimpleName(operation: String, symbol: CialloSymbol, scope: CialloScope): CialloScope {
    if (symbol.path.size != 1) {
      throw CialloRuntimeException("'" + operation + "' needs a simple name, got @" + symbol.name)
    }
    return scope
  }

  /** `left + right`: string concatenation first, then the registered operators. */
  fun addValues(left: CialloValue, right: CialloValue): CialloValue =
    evaluateBinary("+", left, right)

  fun subValues(left: CialloValue, right: CialloValue): CialloValue =
    evaluateBinary("-", left, right)

  fun mulValues(left: CialloValue, right: CialloValue): CialloValue =
    evaluateBinary("*", left, right)

  fun divValues(left: CialloValue, right: CialloValue): CialloValue =
    evaluateBinary("/", left, right)

  fun evaluateBinary(operator: String, left: CialloValue, right: CialloValue): CialloValue {
    val context = context()
    val resolvedLeft = context.resolve(left)
    val resolvedRight = context.resolve(right)
    operations.binary(operator, resolvedLeft, resolvedRight)
      ?.apply(context, operator, resolvedLeft, resolvedRight)
      ?.let { return it }
    throw CialloRuntimeException("unknown operator '" + operator + "'")
  }

  fun evaluateOperator(operator: String, operand: CialloValue): CialloValue {
    val context = context()
    val resolved = context.resolve(operand)
    operations.prefix(operator, resolved)
      ?.apply(context, operator, resolved)
      ?.let { return it }
    throw CialloRuntimeException("unary '" + operator + "' does not accept " + resolved.render())
  }

  fun equalValues(left: CialloValue, right: CialloValue): Boolean =
    CialloValues.equal(resolveForCall(left), resolveForCall(right))

  fun parseNumberSuffix(text: String): CialloValue =
    when (text.last()) {
      'f', 'F' -> CialloFloat(text.removeSuffix("f").removeSuffix("F").toFloat())
      else ->
        operations.numberSuffixParsers().firstNotNullOfOrNull { it.parse(text) }
          ?: throw CialloRuntimeException("invalid number literal '" + text + "'")
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
          builder.append(resolveForCall(value).render())
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

  private fun splitPath(text: String): List<String> = text.split(".")

  private fun lineOf(ctx: ParserRuleContext): Int = ctx.start?.line ?: -1

  private fun columnOf(ctx: ParserRuleContext): Int = ctx.start?.charPositionInLine ?: -1
}
