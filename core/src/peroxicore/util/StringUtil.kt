package peroxicore.util

fun String.capitalize(): String {
  return if (isNotEmpty() && first().isLetter()) {
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
  } else {
    this
  }
}
