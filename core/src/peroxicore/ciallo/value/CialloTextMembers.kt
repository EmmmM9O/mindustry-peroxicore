package peroxicore.ciallo.value

import peroxicore.ciallo.*

private val strTable: CialloMemberTable = memberTable {
  property("size") { _, value -> CialloInt((value as CialloStr).value.length) }

  property("length") { _, value -> CialloInt((value as CialloStr).value.length) }

  method("add") { _, value, args ->
    requireArgs("add", args, 1)
    CialloStr((value as CialloStr).value + args[0].render())
  }

  method("plus") { _, value, args ->
    requireArgs("plus", args, 1)
    CialloStr((value as CialloStr).value + args[0].render())
  }

  method("upper") { _, value, _ -> CialloStr((value as CialloStr).value.uppercase()) }

  method("lower") { _, value, _ -> CialloStr((value as CialloStr).value.lowercase()) }

  method("trim") { _, value, _ -> CialloStr((value as CialloStr).value.trim()) }

  method("toString") { _, value, _ -> value }

  method("contains") { _, value, args ->
    requireArgs("contains", args, 1)
    CialloBool((value as CialloStr).value.contains(args[0].render()))
  }

  method("split") { _, value, args ->
    requireArgs("split", args, 1)
    CialloArr((value as CialloStr).value.split(args[0].render()).mapTo(mutableListOf()) { CialloStr(it) })
  }

  method("startsWith") { _, value, args ->
    requireArgs("startsWith", args, 1)
    CialloBool((value as CialloStr).value.startsWith(args[0].render()))
  }

  method("endsWith") { _, value, args ->
    requireArgs("endsWith", args, 1)
    CialloBool((value as CialloStr).value.endsWith(args[0].render()))
  }

  method("get") { context, value, args ->
    requireArgs("get", args, 1)
    val text = (value as CialloStr).value
    val index = context.int(args[0], "string index")
    if (index !in text.indices) throw CialloRuntimeException("string index " + index + " is out of bounds")
    CialloStr(text[index].toString())
  }

  method("toInt") { _, value, _ ->
    val text = (value as CialloStr).value
    CialloInt(text.trim().toIntOrNull() ?: throw CialloRuntimeException("'" + text + "' is not an Int"))
  }

  method("toFloat") { _, value, _ ->
    val text = (value as CialloStr).value
    CialloFloat(text.trim().toFloatOrNull() ?: throw CialloRuntimeException("'" + text + "' is not a Float"))
  }
}

/** Members of [CialloStr]. */
object CialloStrMembers : CialloMembers by strTable {
  fun memberTableView(): CialloMemberTable = strTable
}
