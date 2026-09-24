package peroxicore.util

fun getCurrentLocation(): String {
  val stackTrace = Throwable().stackTrace
  val element = stackTrace[1]
  return "${element.fileName}:${element.lineNumber}"
}
