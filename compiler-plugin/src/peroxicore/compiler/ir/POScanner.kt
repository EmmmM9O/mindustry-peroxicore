package peroxicore.complier.ir

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.name.*
import peroxicore.complier.*

class POFuncScanner(
  val annotation: ClassId,
  val map: MutableMap<IrSimpleFunction, IrAnnotation>,
  val context: IrPluginContext,
) : IrVisitorVoid() {
  override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    if (declaration.origin != IrDeclarationOrigin.DEFINED) return
    declaration.byId(annotation).firstOrNull()?.let {
      map[declaration] = it
    }
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment,
      -> element.acceptChildrenVoid(this)

      else -> Unit
    }
  }
}

class POClassScanner(
  val annotation: ClassId,
  val map: MutableMap<IrClass, IrAnnotation>,
  val context: IrPluginContext,
) : IrVisitorVoid() {
  override fun visitClass(declaration: IrClass) {
    declaration.byId(annotation).firstOrNull()?.let {
      map[declaration] = it
    }
    declaration.acceptChildrenVoid(this)
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment,
      -> element.acceptChildrenVoid(this)

      else -> Unit
    }
  }
}

class POClassFilter(
  val list: MutableList<IrClass>,
  val context: IrPluginContext,
  val filter: (IrClass) -> Boolean,
) : IrVisitorVoid() {
  override fun visitClass(declaration: IrClass) {
    if (filter(declaration)) list.add(declaration)
    declaration.acceptChildrenVoid(this)
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment,
      -> element.acceptChildrenVoid(this)

      else -> Unit
    }
  }
}

class EntryMethodScanner(
  val plans: Map<
    IrClass,
    MutableList<Pair<IrSimpleFunction, IrAnnotation>>
  >,
  val todos: MutableMap<
    IrSimpleFunction,
    MutableList<Pair<IrSimpleFunction, IrAnnotation>>
  >,
  val context: IrPluginContext,
) : IrVisitorVoid() {
  fun List<IrSimpleFunction>.possible(method: String) = filter { it.name.asString() == method }

  fun List<IrSimpleFunction>.findFunction(
    method: String,
    type: String,
    origin: IrSimpleFunction,
  ): IrSimpleFunction? {
    val candidates = possible(method)
    if (candidates.isEmpty()) return null

    if (type.isEmpty()) {
      if (candidates.size == 1) return candidates.first()
      // 寻找参数类型一样的
      val ptypes = origin.typeStr()
      return candidates.firstOrNull { func -> func.typeStr() == ptypes }
    }
    return candidates.firstOrNull { it.typeStr() == type }
  }

  override fun visitClass(declaration: IrClass) {
    plans[declaration]?.let { funcMap ->
      val allFuncs = declaration.functions()

      funcMap.forEach { (func, anno) ->
        val (name, types) = splitPair(anno.mapping()[AnnoProps.entryMethod]!!.asString())
        allFuncs.findFunction(name, types, func)?.let {
          todos.getOrPut(it) { mutableListOf() }.add(func to anno)
        }
      }
    }
    declaration.acceptChildrenVoid(this)
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment,
      -> element.acceptChildrenVoid(this)

      else -> Unit
    }
  }
}
