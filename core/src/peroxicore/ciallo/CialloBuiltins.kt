package peroxicore.ciallo

/** Standard library of Ciallo. */
object CialloBuiltins {
  fun install(environment: CialloEnvironment) {
    environment.native("print") { context, args ->
      if (args.isEmpty()) {
        environment.printLine("")
        CialloValue.Nil
      } else {
        environment.printLine(context.format(args[0]))
        CialloValue.Nil
      }
    }

    environment.native("then") { context, args ->
      val block = context.block(args.lastOrNull() ?: CialloValue.Nil, "then")
      context.runBlock(block)
    }

    environment.native("run") { context, args ->
      if (args.isEmpty()) context.error("run expects a block")
      val block = context.block(args.last(), "run")
      val bindings =
        when {
          args.size == 1 -> emptyMap()
          args.size == 2 -> mapOf("it" to context.resolve(args[0]))
          else -> context.error("run expects one value and a block")
        }
      context.runBlock(block, bindings)
    }

    environment.native("repeat") { context, args ->
      if (args.size != 2) context.error("repeat expects a count and a block")
      val count = context.int(args[0], "repeat count")
      val block = context.block(args[1], "repeat")
      var index = 0
      while (index < count) {
        context.runBlock(block, mapOf("it" to CialloValue.IntValue(index)))
        index++
      }
      CialloValue.Nil
    }

    environment.native("ifTrue") { context, args ->
      if (args.size != 2) context.error("ifTrue expects a condition and a block")
      if (context.bool(args[0], "ifTrue condition")) {
        context.runBlock(context.block(args[1], "ifTrue"))
      } else {
        CialloValue.Nil
      }
    }

    environment.native("loop") { context, args ->
      if (args.isEmpty()) context.error("loop expects a block")
      val block = context.block(args.last(), "loop")
      if (args.size == 2) {
        val count = context.int(args[0], "loop count")
        var index = 0
        while (index < count) {
          context.runBlock(block, mapOf("it" to CialloValue.IntValue(index)))
          index++
        }
      } else if (args.size == 1) {
        var iterations = 0
        while (true) {
          val result = context.runBlock(block)
          if (!context.bool(result, "loop condition")) break
          iterations++
          if (iterations > 1_000_000) context.error("loop exceeded 1000000 iterations")
        }
      } else {
        context.error("loop expects a block, or a count and a block")
      }
      CialloValue.Nil
    }

    environment.native("not") { context, args ->
      if (args.size != 1) context.error("not expects one argument")
      CialloValue.Bool(!context.bool(args[0], "not"))
    }

    environment.native("add") { context, args ->
      if (args.size != 2) context.error("add expects two arguments")
      context.interpreter.addValues(args[0], args[1])
    }
    environment.native("sub") { context, args ->
      if (args.size != 2) context.error("sub expects two arguments")
      context.interpreter.subValues(args[0], args[1])
    }
    environment.native("mul") { context, args ->
      if (args.size != 2) context.error("mul expects two arguments")
      context.interpreter.mulValues(args[0], args[1])
    }
    environment.native("div") { context, args ->
      if (args.size != 2) context.error("div expects two arguments")
      context.interpreter.divValues(args[0], args[1])
    }


    /*
    environment.native("at", listOf(CialloType.Int, CialloType.Int)) { context, args ->
      CialloValue.Point(context.int(args[0], "at x").toFloat(), context.int(args[1], "at y").toFloat())
    }

    environment.native("at", listOf(CialloType.Float, CialloType.Float)) { context, args ->
      CialloValue.Vec2(context.float(args[0], "at x"), context.float(args[1], "at y"))
    }

    environment.native("label") { context, args ->
      val value = args.firstOrNull() ?: CialloValue.Nil
      val text = context.format(value)
      environment.printLine(text)
      val action = CialloValue.LabelAction(text)
      environment.scene.labels += action
      action
    }

    environment.native("target") { context, args ->
      if (args.size != 1) context.error("target expects one argument")
      val value = context.resolve(args[0])
      environment.scene.target = value
      value
    }

    environment.native("build") { context, args ->
      if (args.size != 1) context.error("build expects one block argument")
      val action = CialloValue.BuildAction(context.resolve(args[0]))
      environment.scene.builds += action
      action
    }

    environment.native("world") { context, args ->
      val block = context.block(args.lastOrNull() ?: CialloValue.Nil, "world")
      val world = CialloWorld()
      environment.scene.world = world
      val bindings =
        linkedMapOf<String, CialloValue>(
          "x" to CialloValue.IntValue(0),
          "y" to CialloValue.IntValue(0),
          "size" to nativeValue("size") { innerContext, innerArgs ->
            if (innerArgs.size != 2) innerContext.error("size expects width and height")
            world.width = innerContext.int(innerArgs[0], "size width")
            world.height = innerContext.int(innerArgs[1], "size height")
            CialloValue.Nil
          },
          "width" to nativeValue("width") { innerContext, innerArgs ->
            if (innerArgs.size != 1) innerContext.error("width expects one argument")
            world.width = innerContext.int(innerArgs[0], "width")
            innerArgs[0]
          },
          "height" to nativeValue("height") { innerContext, innerArgs ->
            if (innerArgs.size != 1) innerContext.error("height expects one argument")
            world.height = innerContext.int(innerArgs[0], "height")
            innerArgs[0]
          },
          "fill" to nativeValue("fill") { innerContext, innerArgs ->
            if (innerArgs.size != 1) innerContext.error("fill expects one argument")
            world.fill = innerContext.resolve(innerArgs[0])
            innerArgs[0]
          },
        )
      context.runBlock(block, bindings)
      CialloValue.Nil
    }

    environment.native("frame") { context, args ->
      if (args.isEmpty()) context.error("frame expects a block")
      val block = context.block(args.last(), "frame")
      val time = if (args.size > 1) context.resolve(args[0]) as? CialloValue.TimeValue else null
      val frame = CialloValue.FrameAction(time)
      environment.scene.frames += frame
      val bindings = if (time != null) mapOf("it" to time) else emptyMap()
      context.runBlock(block, bindings)
      frame
    }*/
  }

  private fun nativeValue(
    name: String,
    function: (CialloCallContext, List<CialloValue>) -> CialloValue,
  ): CialloValue.NativeValue =
    CialloValue.NativeValue(name, CialloNative(name, CialloNativeFunction(function)))
}
