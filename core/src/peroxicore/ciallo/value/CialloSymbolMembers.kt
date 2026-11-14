package peroxicore.ciallo.value

import peroxicore.ciallo.*

private fun variableName(operation: String, symbol: CialloSymbol): String {
  if (symbol.path.size != 1) {
    throw CialloRuntimeException("'" + operation + "' needs a simple name, got @" + symbol.name)
  }
  return symbol.path[0]
}

private val symbolTable: CialloMemberTable = memberTable {
  method("local") { context, symbol, args ->
    requireArgs("local", args, 1)
    val resolved = context.resolve(args[0])
    context.interpreter.localScopeFor(symbol as CialloSymbol).defineLocal(variableName("local", symbol), resolved)
    resolved
  }

  method("mount") { context, symbol, args ->
    requireArgs("mount", args, 1)
    val resolved = context.resolve(args[0])
    context.interpreter.globalScopeOf(symbol as CialloSymbol).defineGlobal(variableName("mount", symbol), resolved)
    resolved
  }

  method("set") { context, symbol, args ->
    requireArgs("set", args, 1)
    context.interpreter.assignSymbol(symbol as CialloSymbol, context.resolve(args[0]))
  }
}

/**
 * A name literal is not a value: `@x` is resolved on demand.  It has no
 * properties; the three variable operations are its only members.
 */
object CialloSymbolMembers : CialloMembers by symbolTable
