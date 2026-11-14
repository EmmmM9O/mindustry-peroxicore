package peroxicore.ciallo.value

import peroxicore.ciallo.*

private val arrTable: CialloMemberTable = memberTable {
  property("size") { _, value -> CialloInt((value as CialloArr).items.size) }

  property("length") { _, value -> CialloInt((value as CialloArr).items.size) }

  property("first") { _, value -> (value as CialloArr).items.firstOrNull() ?: CialloNil.INSTANCE }

  property("last") { _, value -> (value as CialloArr).items.lastOrNull() ?: CialloNil.INSTANCE }

  property("empty") { _, value -> CialloBool((value as CialloArr).items.isEmpty()) }

  /** Enters the array as `: [...this]{ @it ... }` with `it`, `index` and `acc` in scope. */
  method("apply") { context, value, args ->
    requireArgs("apply", args, 1)
    val block = context.block(args[0], "apply")
    context.scopeWith(
      block,
      mapOf("it" to value, "index" to CialloInt(-1), "acc" to value),
      body = {
        context.interpreter.executeBlockInScope(block.block, context.interpreter.currentScope)
        value
      },
    )
  }

  method("add") { _, value, args ->
    requireArgs("add", args, 1)
    (value as CialloArr).items += args[0]
    value
  }

  method("get") { context, value, args ->
    requireArgs("get", args, 1)
    val items = (value as CialloArr).items
    items[context.index(args[0], items.size)]
  }

  method("set") { context, value, args ->
    requireArgs("set", args, 2)
    val items = (value as CialloArr).items
    items[context.index(args[0], items.size)] = args[1]
    value
  }

  method("remove") { context, value, args ->
    requireArgs("remove", args, 1)
    val items = (value as CialloArr).items
    items.removeAt(context.index(args[0], items.size))
    value
  }

  method("clear") { _, value, _ ->
    (value as CialloArr).items.clear()
    value
  }

  method("join") { _, value, args ->
    val separator = if (args.isEmpty()) "" else args[0].render()
    CialloStr((value as CialloArr).items.joinToString(separator) { it.render() })
  }

  method("contains") { context, value, args ->
    requireArgs("contains", args, 1)
    CialloBool((value as CialloArr).items.any { context.equals(it, args[0]) })
  }

  method("indexOf") { context, value, args ->
    requireArgs("indexOf", args, 1)
    CialloInt((value as CialloArr).items.indexOfFirst { context.equals(it, args[0]) })
  }

  method("toString") { _, value, _ -> CialloStr(value.render()) }

  /**
   * Runs a block for every element in the array's scope with `it`, `index`
   * and `acc` (the array being built) bound.
   */
  method("map") { context, value, args ->
    requireArgs("map", args, 1)
    val block = context.block(args[0], "map")
    val result = CialloArr()
    for ((index, item) in (value as CialloArr).items.toList().withIndex()) {
      val bindings = mapOf("it" to item, "index" to CialloInt(index), "acc" to result)
      result.items += context.runBlockInScope(block, bindings)
    }
    result
  }

  method("filter") { context, value, args ->
    requireArgs("filter", args, 1)
    val block = context.block(args[0], "filter")
    val result = CialloArr()
    for ((index, item) in (value as CialloArr).items.toList().withIndex()) {
      val bindings = mapOf("it" to item, "index" to CialloInt(index), "acc" to result)
      if (context.bool(context.runBlockInScope(block, bindings), "filter")) {
        result.items += item
      }
    }
    result
  }

  method("each") { context, value, args ->
    requireArgs("each", args, 1)
    val block = context.block(args[0], "each")
    for ((index, item) in (value as CialloArr).items.toList().withIndex()) {
      context.runBlockInScope(block, mapOf("it" to item, "index" to CialloInt(index), "acc" to value))
    }
    value
  }
}

private val objTable: CialloMemberTable = memberTable {
  /** The fields are the object's members; a miss is simply `null`. */
  memberFallback { _, value, name -> (value as? CialloObj)?.fields?.get(name) }

  callFallback { context, value, name, args ->
    val field = (value as? CialloObj)?.fields?.get(name) ?: return@callFallback null
    if (field is CialloNativeValue) return@callFallback field.native.invoke(context, args)
    if (args.isEmpty()) return@callFallback field
    throw CialloRuntimeException("field '$name' is not a function")
  }

  /** Enters the object as `: this{ @x ... }`; its fields shadow the block's closure. */
  method("apply") { context, value, args ->
    requireArgs("apply", args, 1)
    val block = context.block(args[0], "apply")
    context.scopeWith(
      block,
      emptyMap(),
      CialloObjScopeBacking(value as CialloObj),
      body = {
        // The block runs in the custom scope itself, not a fresh child of its closure.
        context.interpreter.executeBlockInScope(block.block, context.interpreter.currentScope)
        value
      },
    )
  }

  method("get") { _, value, args ->
    requireArgs("get", args, 1)
    val name = args[0].render()
    (value as CialloObj).fields[name] ?: throw CialloRuntimeException("object has no field '" + name + "'")
  }

  method("has") { _, value, args ->
    requireArgs("has", args, 1)
    CialloBool((value as CialloObj).fields.containsKey(args[0].render()))
  }

  method("put") { _, value, args ->
    requireArgs("put", args, 2)
    (value as CialloObj).fields[args[0].render()] = args[1]
    value
  }

  method("keys") { _, value, _ ->
    CialloArr((value as CialloObj).fields.keys.mapTo(mutableListOf()) { CialloStr(it) })
  }

  method("values") { _, value, _ -> CialloArr((value as CialloObj).fields.values.toMutableList()) }

  method("toString") { _, value, _ -> CialloStr(value.render()) }
}

/** Members of [CialloArr]. */
object CialloArrMembers : CialloMembers by arrTable {
  fun memberTableView(): CialloMemberTable = arrTable
}

/** Members of [CialloObj]: its fields, plus a few helpers. */
object CialloObjMembers : CialloMembers by objTable
