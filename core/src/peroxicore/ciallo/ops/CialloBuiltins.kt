package peroxicore.ciallo.ops

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/**
 * The standard library of Ciallo: `print`, the block runners, the loop helpers
 * and the arithmetic free functions.
 *
 * Each builtin is a [CialloFreeFunction] component.  Registering another
 * component with the same [CialloFreeFunction.name] replaces it.
 */
object CialloBuiltins {
  /** One instance per builtin, so [CialloOperations.addOnce] never duplicates them. */
  val components: List<CialloFreeFunction> by lazy {
    listOf(
      print,
      then,
      run,
      repeat,
      ifTrue,
      loop,
      not,
      binary("add") { context, left, right -> context.interpreter.addValues(left, right) },
      binary("sub") { context, left, right -> context.interpreter.subValues(left, right) },
      binary("mul") { context, left, right -> context.interpreter.mulValues(left, right) },
      binary("div") { context, left, right -> context.interpreter.divValues(left, right) },
    )
  }

  val print: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "print"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue {
        if (args.isEmpty()) {
          context.environment.printLine("")
        } else {
          context.environment.printLine(context.format(args[0]))
        }
        return CialloNil.INSTANCE
      }
    }

  val then: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "then"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue =
        context.runBlock(args.lastOrNull() ?: CialloNil.INSTANCE)
    }

  val run: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "run"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue =
        when (args.size) {
          0 -> context.error("run expects a block")
          1 -> context.runBlock(args[0])
          2 -> context.runBlock(context.block(args[1], "run"), mapOf("it" to context.resolve(args[0])))
          else -> context.error("run expects one value and a block")
        }
    }

  val repeat: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "repeat"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue {
        if (args.size != 2) context.error("repeat expects a count and a block")
        val count = context.int(args[0], "repeat count")
        val block = context.block(args[1], "repeat")
        var index = 0
        while (index < count) {
          context.runBlockInScope(block, mapOf("it" to CialloInt(index)))
          index++
        }
        return CialloNil.INSTANCE
      }
    }

  val ifTrue: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "ifTrue"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue {
        if (args.size != 2) context.error("ifTrue expects a condition and a block")
        if (context.bool(args[0], "ifTrue condition")) {
          context.runBlock(context.block(args[1], "ifTrue"))
        }
        return CialloNil.INSTANCE
      }
    }

  val loop: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "loop"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue {
        if (args.isEmpty()) context.error("loop expects a block")
        val block = context.block(args.last(), "loop")
        when (args.size) {
          2 -> {
            val count = context.int(args[0], "loop count")
            var index = 0
            while (index < count) {
              context.runBlockInScope(block, mapOf("it" to CialloInt(index)))
              index++
            }
          }
          1 -> {
            var iterations = 0
            while (true) {
              val result = context.runBlockInScope(block)
              if (!context.bool(result, "loop condition")) break
              iterations++
              if (iterations > 1_000_000) context.error("loop exceeded 1000000 iterations")
            }
          }
          else -> context.error("loop expects a block, or a count and a block")
        }
        return CialloNil.INSTANCE
      }
    }

  val not: CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = "not"

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue {
        if (args.size != 1) context.error("not expects one argument")
        return CialloBool(!context.bool(args[0], "not"))
      }
    }

  private fun binary(
    functionName: String,
    body: (CialloCallContext, CialloValue, CialloValue) -> CialloValue,
  ): CialloFreeFunction =
    object : CialloFreeFunction {
      override val name: String get() = functionName

      override fun call(context: CialloCallContext, args: List<CialloValue>): CialloValue {
        if (args.size != 2) context.error(functionName + " expects two arguments")
        return body(context, args[0], args[1])
      }
    }
}
