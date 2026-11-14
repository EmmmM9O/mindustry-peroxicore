package peroxicore.ciallo.value

import peroxicore.ciallo.*

private fun blockBindings(args: List<CialloValue>): Map<String, CialloValue> =
  when (args.size) {
    0 -> emptyMap()
    1 -> mapOf("it" to args[0])
    else -> throw CialloRuntimeException("a block takes at most one argument, got " + args.size)
  }

private val blockTable: CialloMemberTable = memberTable {
  method("run") { context, value, args ->
    context.interpreter.executeBlock((value as CialloBlockValue).block, blockBindings(args))
  }

  method("then") { context, value, args ->
    context.interpreter.executeBlock((value as CialloBlockValue).block, blockBindings(args))
  }

  method("apply") { context, value, args ->
    context.interpreter.executeBlock((value as CialloBlockValue).block, blockBindings(args))
  }
}

private val nilTable: CialloMemberTable = memberTable {
  method("toString") { _, _, _ -> CialloStr("null") }

  method("isNil") { _, _, _ -> CialloBool(true) }
}

private val boolTable: CialloMemberTable = memberTable {
  method("not") { _, value, _ -> CialloBool(!(value as CialloBool).value) }

  method("toString") { _, value, _ -> CialloStr(value.render()) }
}

/** A native function is only callable by name; it has no members. */
private val nativeTable: CialloMemberTable = memberTable {}

/**
 * Host JVM objects answer members through the registered [CialloJavaBridge],
 * which also decides whether the sandbox allows the access.
 */
private val javaTable: CialloMemberTable = memberTable {
  memberFallback { context, value, name ->
    context.environment.javaBridge.getMember((value as CialloJavaValue).value, name, context.environment.sandbox)
  }

  callFallback { context, value, name, args ->
    val target = (value as CialloJavaValue).value
    if (name == "apply") {
      val block = args.singleOrNull() as? CialloBlockValue
      if (block != null) return@callFallback context.environment.javaBridge.applyBlock(context, target, block)
    }
    context.environment.javaBridge.callMember(target, name, args, context.environment.sandbox)
  }
}

private val timeTable: CialloMemberTable = memberTable {
  property("millis") { _, value -> CialloInt((value as CialloTime).millis.toInt()) }

  property("seconds") { _, value -> CialloFloat(((value as CialloTime).millis / 1000.0).toFloat()) }

  property("amount") { _, value -> CialloFloat((value as CialloTime).amount.toFloat()) }

  property("unit") { _, value -> CialloStr((value as CialloTime).unit.suffix) }

  method("toMillis") { _, value, _ -> CialloInt((value as CialloTime).millis.toInt()) }

  method("toSeconds") { _, value, _ -> CialloFloat(((value as CialloTime).millis / 1000.0).toFloat()) }

  method("toString") { _, value, _ -> CialloStr(value.render()) }

  method("add") { _, value, args ->
    requireArgs("add", args, 1)
    val time = value as CialloTime
    val other = args[0] as? CialloTime ?: throw CialloRuntimeException("time.add expects another Time")
    CialloTime(time.amount + other.amount, time.unit)
  }

  method("sub") { _, value, args ->
    requireArgs("sub", args, 1)
    val time = value as CialloTime
    val other = args[0] as? CialloTime ?: throw CialloRuntimeException("time.sub expects another Time")
    CialloTime(time.amount - other.amount, time.unit)
  }
}

/**
 * A block is not a function.  It carries its own closure and can only be run,
 * `: {...}` or `: (value){...}`, against `it`.
 */
object CialloBlockMembers : CialloMembers by blockTable

/** Nil answers the same names as any other value, so chains stay total. */
object CialloNilMembers : CialloMembers by nilTable

object CialloBoolMembers : CialloMembers by boolTable

object CialloNativeMembers : CialloMembers by nativeTable

object CialloJavaMembers : CialloMembers by javaTable

object CialloTimeMembers : CialloMembers by timeTable
