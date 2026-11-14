package peroxicore.ciallo.ops

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/**
 * A pluggable behaviour of the runtime.
 *
 * The interpreter never switches on value types itself: it asks
 * [CialloOperations] for the component that handles a request, so host code
 * extends the language by registering a component instead of editing the
 * interpreter.
 *
 * Every handler receives the arguments exactly as the caller wrote them and is
 * expected to resolve what it consumes through [CialloCallContext]; this lets
 * every component see the same argument list.  A handler returns `null` only
 * when it does not handle the request.
 */
interface CialloOperation {
  /** A readable label, used by diagnostics. */
  val label: String get() = javaClass.simpleName
}

/** A binary operator; [operator] is the source spelling such as `+` or `==`. */
interface CialloBinaryOperator : CialloOperation {
  fun accepts(operator: String, left: CialloValue, right: CialloValue): Boolean

  fun apply(context: CialloCallContext, operator: String, left: CialloValue, right: CialloValue): CialloValue?
}

/** A prefix operator; [operator] is the source spelling such as `-` or `!`. */
interface CialloPrefixOperator : CialloOperation {
  fun accepts(operator: String, operand: CialloValue): Boolean

  fun apply(context: CialloCallContext, operator: String, operand: CialloValue): CialloValue?
}

/** A free function call such as `print(...)` that is not dispatched to a member. */
interface CialloFreeFunction : CialloOperation {
  val name: String

  fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue?
}

/**
 * A member request, `receiver.name` or `receiver.name(args)`, handled before the
 * receiver's own member table.  A handler that returns `null` declines.
 */
interface CialloMemberHandler : CialloOperation {
  fun call(context: CialloCallContext, receiver: CialloValue, name: String, args: List<CialloValue>?): CialloValue?
}

/** Converts a number literal token that carries a suffix, such as `1f` or `5s`. */
fun interface CialloNumberSuffixParser : CialloOperation {
  fun parse(token: String): CialloValue?
}

/** Resolves a name that the scopes and registered symbols could not resolve. */
interface CialloNameResolver : CialloOperation {
  fun resolve(interpreter: CialloInterpreter, path: List<String>): CialloValue?
}

/**
 * An ordered component registry.
 *
 * Later registrations win, so a host overrides a default by registering its own
 * component for the same request.
 */
class CialloOperations {
  private val binaryOperators = mutableListOf<CialloBinaryOperator>()
  private val prefixOperators = mutableListOf<CialloPrefixOperator>()
  private val freeFunctions = mutableListOf<CialloFreeFunction>()
  private val memberHandlers = mutableListOf<CialloMemberHandler>()
  private val numberSuffixParsers = mutableListOf<CialloNumberSuffixParser>()
  private val nameResolvers = mutableListOf<CialloNameResolver>()

  fun binary(operator: String, left: CialloValue, right: CialloValue): CialloBinaryOperator? =
    binaryOperators.lastOrNull { it.accepts(operator, left, right) }

  fun prefix(operator: String, operand: CialloValue): CialloPrefixOperator? =
    prefixOperators.lastOrNull { it.accepts(operator, operand) }

  fun freeFunction(name: String): CialloFreeFunction? = freeFunctions.lastOrNull { it.name == name }

  /** Every registered free function name; used by completion. */
  fun freeFunctionNames(): Collection<String> = freeFunctions.map { it.name }

  /** The member handler registered for `receiver.name`, if any. */
  fun memberHandler(receiver: CialloValue, name: String): CialloMemberHandler? =
    memberHandlers.lastOrNull { it.handles(receiver, name) }

  /** The member table that advertises `receiver.name`, if any. */
  fun memberTable(receiver: CialloValue, name: String): CialloMemberTable? =
    receiver.memberTableView()?.takeIf { it.open || it.canRead(name) || it.canCall(name) }

  fun numberSuffixParsers(): List<CialloNumberSuffixParser> = numberSuffixParsers.toList()

  fun symbolResolvers(): List<CialloNameResolver> = nameResolvers.toList()

  fun add(operation: CialloOperation) {
    when (operation) {
      is CialloBinaryOperator -> binaryOperators += operation
      is CialloPrefixOperator -> prefixOperators += operation
      is CialloFreeFunction -> freeFunctions += operation
      is CialloMemberHandler -> memberHandlers += operation
      is CialloNumberSuffixParser -> numberSuffixParsers += operation
      is CialloNameResolver -> nameResolvers += operation
    }
  }

  fun addAll(operations: Iterable<CialloOperation>) = operations.forEach { add(it) }

  /** Registers [component] only if it is not already installed (by instance). */
  fun addOnce(component: CialloOperation) {
    if (!installed(component)) add(component)
  }

  fun installed(component: CialloOperation): Boolean =
    binaryOperators.contains(component) ||
      prefixOperators.contains(component) ||
      freeFunctions.contains(component) ||
      memberHandlers.contains(component) ||
      numberSuffixParsers.contains(component) ||
      nameResolvers.contains(component)

  companion object {
    /** The default runtime: numbers, strings, ordering, negation and name resolution. */
    fun standard(): CialloOperations =
      CialloOperations().also { operations ->
        operations.addAll(
          listOf(
            CialloArithmetic,
            CialloConcatenation,
            CialloOrdering,
            CialloNegation,
            CialloLogicalNot,
            CialloSymbolNames,
            CialloTimeLiterals,
          )
        )
      }
  }
}
