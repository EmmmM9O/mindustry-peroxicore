package peroxicore

import com.google.devtools.ksp.symbol.*

@JvmInline
value class PXCAnnotatin(
  val annotation: KSAnnotation?,
) {
  companion object {
    val empty = PXCAnnotatin(null)
  }
}

fun Pair<KSAnnotated?, KSAnnotation?>.toPXC() = second?.let(::PXCAnnotatin) ?: PXCAnnotatin.empty

fun KSAnnotation.toPXC() = PXCAnnotatin(this)

inline fun <reified T, R> PXCAnnotatin.valueOp(
  fieldName: String,
  default: R,
  supplier: (T) -> R,
) = (annotation?.arguments?.find { it.name?.asString() == fieldName }?.value as? T)?.let(supplier)
  ?: default

inline fun <reified T> PXCAnnotatin.value(
  fieldName: String,
  default: T,
) = annotation?.arguments?.find { it.name?.asString() == fieldName }?.value as? T ?: default

inline fun <reified T> PXCAnnotatin.valueNull(fieldName: String): T? =
  annotation?.arguments?.find { it.name?.asString() == fieldName }?.value as? T

fun PXCAnnotatin.bool(
  fieldName: String,
  default: Boolean,
) = value(fieldName, default)

fun PXCAnnotatin.float(
  fieldName: String,
  default: Float,
) = value(fieldName, default)

fun PXCAnnotatin.string(
  fieldName: String,
  default: String,
) = value(fieldName, default)

fun PXCAnnotatin.stringNull(fieldName: String) = valueNull<String>(fieldName)

fun PXCAnnotatin.stringArr(
  fieldName: String,
  default: List<String>,
) = value(fieldName, default)

inline fun <R> PXCAnnotatin.valueParse(
  fieldName: String,
  parser: (PXCAnnotatin) -> R,
) = valueOp<KSAnnotation, PXCAnnotatin>(fieldName, PXCAnnotatin.empty, ::PXCAnnotatin).let(parser)
