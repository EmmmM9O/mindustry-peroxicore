package peroxicore.complier.ir

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrAnnotation.annoClassId() = symbol.owner.parentAsClass.classId

fun IrAnnotationContainer.byId(annotation: ClassId) =
  annotations.filter {
    it.annoClassId() == annotation
  }

fun IrAnnotationContainer.firstById(annotation: ClassId) = byId(annotation).firstOrNull()

fun IrAnnotationContainer.has(annotation: ClassId) = byId(annotation).isNotEmpty()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrDeclarationContainer.functions() = declarations.filterIsInstance<IrSimpleFunction>()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrDeclarationContainer.properties() = declarations.filterIsInstance<IrProperty>()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClass.superClasses() =
  superTypes
    .filterIsInstance<IrSimpleType>()
    .map { it.classifier.owner }
    .filterIsInstance<IrClass>()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClass.interfaceAncestors(): List<IrClass> =
  superClasses()
    .filter { it.kind == ClassKind.INTERFACE }
    .flatMap { listOf(it, *it.interfaceAncestors().toTypedArray()) }

// readonlyd
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrAnnotation.mapping() =
  symbol.owner.parameters.associate { it.name to (arguments[it] ?: it.defaultValue?.expression) }

fun IrExpression.asString() = (this as IrConst).value as String

fun IrExpression.asBoolean() = (this as IrConst).value as Boolean

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrExpression.forString() =
  when (this) {
    is IrConst -> value.toString()
    is IrGetEnumValue -> symbol.owner.name.asString()
    else -> "unknown"
  }

fun IrExpression.stringArr(): List<String> =
  when (this) {
    is IrVararg -> {
      elements.map { element ->
        (element as IrConst).value as String
      }
    }

    else -> {
      emptyList()
    }
  }

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrSimpleType.asStr() = (classifier.owner as IrClass).classId!!.asSingleFqName().asString()

fun IrFunction.typeStr() =
  parameters.drop(1).joinToString(",") { (it.type as IrSimpleType).asStr() }

fun splitPair(input: String): Pair<String, String> {
  val idx = input.indexOf(':')
  return if (idx == -1) input to "" else input.substring(0, idx) to input.substring(idx + 1)
}
