package peroxicore.complier.ir

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.js.resolve.diagnostics.*
import peroxicore.complier.*

typealias FunctionInfo = Pair<IrSimpleFunction, IrAnnotation>

@OptIn(UnsafeDuringIrConstructionAPI::class)
class POIrGenerationExtension(
  val messageCollector: MessageCollector,
) : IrGenerationExtension {
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    val annotation = Annotations.MethodEntry
    if (pluginContext.finderForBuiltins().findClass(annotation) == null) {
      moduleFragment.reportError("Could not find class <$annotation>")
      return
    }

    val implEntries = mutableListOf<IrClass>()
    val methodEntry = mutableMapOf<IrSimpleFunction, IrAnnotation>()
    val plans =
      mutableMapOf<
        IrClass,
        MutableList<FunctionInfo>
      >()

    moduleFragment.acceptChildrenVoid(
      POFuncScanner(Annotations.MethodEntry, methodEntry, pluginContext)
    )

    val comps = methodEntry.keys.mapNotNull { it.parent as? IrClass }
    moduleFragment.acceptChildrenVoid(
      POClassFilter(implEntries, pluginContext) { impl ->
        impl.interfaceAncestors().any { it in comps }
      }
    )

    implEntries.forEach { implClass ->
      val stypes = implClass.interfaceAncestors()
      // List<IrSimpleFunction, IrAnnotation>
      plans[implClass] =
        stypes
          .flatMap { comp ->
            comp
              .functions()
              .filter { it.origin == IrDeclarationOrigin.DEFINED }
              .mapNotNull { func ->
                func.firstById(Annotations.MethodEntry)?.let { func to it }
              }
          }.toMutableList()
    }
    val todos =
      mutableMapOf<
        IrSimpleFunction,
        MutableList<FunctionInfo>
      >()

    moduleFragment.acceptChildrenVoid(
      EntryMethodScanner(
        plans,
        todos,
        pluginContext
      )
    )

    todos.forEach { (implFunc, compFuncs) ->
      val implClass = implFunc.parent as IrClass
      val implClassName = implClass.classId!!.asSingleFqName().asString()
      val implFuncName = implFunc.name.asString()
      val implParams = implFunc.parameters.associateBy { it.name.asString() }
      val properties = implClass.properties().associate { it.name.asString() to it.symbol }

      implFunc.body =
        DeclarationIrBuilder(generatorContext = pluginContext, symbol = implFunc.symbol)
          .irBlockBody {
            var overrideIr: FunctionInfo? = null
            val callListHead = mutableListOf<FunctionInfo>()
            val callListEnd = mutableListOf<FunctionInfo>()
            val injects = mutableListOf<FunctionInfo>()
            val origin = implFunc.origin
            val emptyBody =
              origin is GeneratedByPlugin && origin.pluginKey == PluginKeys.methodEntry

            fun IrBlockBodyBuilder.gen(
              pair: FunctionInfo,
              superCall: IrExpression?,
            ): IrExpression {
              val (compFunc, anno) = pair
              val compPN =
                compFunc.parentAsClass.classId!!
                  .asSingleFqName()
                  .asString()
              val compFuncName = compFunc.name.asString()
              val map = anno.mapping()
              val params = map[AnnoProps.params]!!.stringArr()
              val context = map[AnnoProps.context]!!.stringArr()

              return irCall(compFunc).apply {
                dispatchReceiver = irGet(implFunc.dispatchReceiverParameter!!)

                params.forEachIndexed { index, param ->
                  if (param.isEmpty()) return@forEachIndexed
                  param.toIntOrNull()?.let {
                    arguments[index + 1] = irGet(implFunc.parameters.get(it))
                    return@forEachIndexed
                  }
                  if (param == "super()") {
                    arguments[index + 1] = superCall
                    return@forEachIndexed
                  }
                  val vparam =
                    implParams[param]?.also {
                      arguments[index + 1] = irGet(it)
                    }
                      ?: run {
                        messageCollector.report(
                          CompilerMessageSeverity.ERROR,
                          "Function `$implClassName.$implFuncName` have no `$param` for `$compPN.$compFuncName` "
                        )
                      }
                }

                if (params.isEmpty() && implFunc.typeStr() == compFunc.typeStr()) {
                  implFunc.parameters.drop(1).forEachIndexed { index, param ->
                    arguments[index + 1] = irGet(param)
                  }
                }

                context.forEachIndexed { index, property ->
                  if (property.isEmpty()) return@forEachIndexed
                  val vproperty =
                    properties[property]?.owner?.getter?.symbol?.also {
                      arguments[index + 1] =
                        irCall(it).apply {
                          dispatchReceiver = irGet(implFunc.dispatchReceiverParameter!!)
                        }
                    }
                      ?: run {
                        messageCollector.report(
                          CompilerMessageSeverity.ERROR,
                          "Class `$implClassName.$property` not exists.But needed for `$compPN.$compFuncName` "
                        )
                      }
                }
              }
            }

            fun IrBlockBodyBuilder.addCall(
              info: FunctionInfo,
              superCall: IrExpression?,
            ) {
              +gen(info, superCall)
            }

            fun IrBlockBodyBuilder.addCall(
              list: List<FunctionInfo>,
              superCall: IrExpression?,
            ) {
              list.forEach {
                +gen(it, superCall)
              }
            }

            compFuncs.forEach { pair ->
              val (compFunc, anno) = pair
              val map = anno.mapping()
              val compPN =
                compFunc.parentAsClass.classId!!
                  .asSingleFqName()
                  .asString()
              val compFuncName = compFunc.name.asString()
              val pos = map[AnnoProps.insert]!!.forString()
              val params = map[AnnoProps.params]!!.stringArr()
              val context = map[AnnoProps.context]!!.stringArr()
              when (pos) {
                "HEAD" -> {
                  callListHead.add(pair)
                }

                "END" -> {
                  callListEnd.add(pair)
                }

                "INJECT" -> {
                  if (compFunc.returnType.isUnit()) {
                    messageCollector.report(
                      CompilerMessageSeverity.WARNING,
                      "`$compPN.$compFuncName` could not inject `Unit` `$implClassName.$implFuncName`. skip"
                    )
                  } else {
                    injects.add(pair)
                  }
                }

                "OVERRIDE" -> {
                  overrideIr = pair
                  if (!emptyBody) {
                    overrideIr = null
                    messageCollector.report(
                      CompilerMessageSeverity.WARNING,
                      "`$compPN.$compFuncName` could not override the result of `$implClassName.$implFuncName`. skip"
                    )
                  }
                }
              }

              params.forEach { param ->
                if (param == "super()") {
                  when (pos) {
                    "OVERRIDE" -> {
                      messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "`$compPN.$compFuncName` could call `super` becauseof its insert position `$pos`"
                      )
                    }
                  }
                  return@forEach
                }
              }
            }

            info(
              "IR ${if (overrideIr == null) {
                if (injects.isNotEmpty()) "Inject" else "Fill"
              } else {
                "Override"
              }}: `$implClassName.$implFuncName(${implFunc.typeStr()})` with ${compFuncs.map{
                "${it.first.name.asString()}(${it.first.typeStr()})"
              }.joinToString{"`$it`"}}"
            )

            fun IrBlockBodyBuilder.callInjects(superCall: IrExpression): IrExpression =
              injects.fold(superCall) { acc, inject ->
                gen(inject, acc)
              }

            if (overrideIr != null) {
              if (implFunc.returnType.isUnit()) {
                addCall(callListHead, null)
                addCall(overrideIr, null)
                addCall(callListEnd, null)
              } else {
                addCall(callListHead, null)
                val resultVariable =
                  irTemporary(
                    callInjects(gen(overrideIr, null)),
                    "peroxicore.complier_tmp_result"
                  )
                addCall(callListEnd, irGet(resultVariable))
                +irReturn(irGet(resultVariable))
              }
            } else if (emptyBody) {
              // EmptyBody
              val parentF =
                implFunc.overriddenSymbols.firstOrNull()
                  ?: run {
                    messageCollector.report(
                      CompilerMessageSeverity.ERROR,
                      "Function '$implClassName.$implFuncName` needed.But not find in itself or its parents"
                    )
                    return
                  }
              val parentC = implClass.superClasses().first { it.kind == ClassKind.CLASS }
              val supF =
                irCall(parentF).apply {
                  // 希望没有泛型 头疼…
                  superQualifierSymbol = parentC.symbol
                  dispatchReceiver = irGet(implFunc.dispatchReceiverParameter!!)

                  parentF.owner.parameters.zip(implFunc.parameters.map { irGet(it) }).forEach {
                    (index, expr),
                    ->
                    arguments[index] = expr
                  }
                }
              if (implFunc.returnType.isUnit()) {
                addCall(callListHead, null)
                +supF
                addCall(callListEnd, null)
              } else {
                addCall(callListHead, null)
                val resultVariable =
                  irTemporary(
                    callInjects(supF),
                    "peroxicore.complier_tmp_result"
                  )
                addCall(callListEnd, irGet(resultVariable))
                +irReturn(irGet(resultVariable))
              }
            } else {
              // New body
              when (val oldBody = implFunc.body) {
                is IrBlockBody -> {
                  addCall(callListHead, null)
                  if (implFunc.returnType.isUnit()) {
                    +oldBody.statements
                    addCall(callListEnd, null)
                  } else {
                    oldBody.statements.apply {
                      val returnIndex = indexOfLast { it is IrReturn }
                      if (returnIndex >= 0) {
                        +take(returnIndex)
                        val resultVariable =
                          irTemporary(
                            callInjects((this[returnIndex] as IrReturn).value),
                            "peroxicore.complier_tmp_result"
                          )
                        addCall(callListEnd, irGet(resultVariable))
                        +irReturn(irGet(resultVariable))
                      } else {
                        +this
                        addCall(callListEnd, null)
                      }
                    }
                  }
                }

                is IrExpressionBody -> {
                  addCall(callListHead, null)
                  val resultVariable =
                    irTemporary(
                      callInjects(oldBody.expression),
                      "peroxicore.complier_tmp_result"
                    )
                  addCall(callListEnd, irGet(resultVariable))
                  +irReturn(irGet(resultVariable))
                }

                else -> {
                  if (oldBody != null) {
                    messageCollector.report(
                      CompilerMessageSeverity.WARNING,
                      "Skip body of `$implClassName.$implFuncName` due to unknwn body `$oldBody`"
                    )
                  }
                  addCall(callListHead, null)
                  addCall(callListEnd, null)
                }
              }
            }
          }
    }
  }

  fun info(text: String) {
    // 我没有其他办法输出信息了
    messageCollector.report(
      CompilerMessageSeverity.WARNING,
      "[PREOXIDE-INFO]: $text"
    )
    System.err.println("[INFO]: $text")
  }

  private fun IrModuleFragment.reportError(message: String) {
    val psi = descriptor.findPsi()
    val location = MessageUtil.psiElementToMessageLocation(psi)
    messageCollector.report(CompilerMessageSeverity.ERROR, message, location)
  }
}
