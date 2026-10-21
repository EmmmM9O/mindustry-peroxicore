package peroxicore.util

/** A resolved position in source code. [UNKNOWN] is used when capturing was skipped or failed. */
data class SourceLocation(
  val fileName: String? = null,
  val className: String? = null,
  val methodName: String? = null,
  val lineNumber: Int = -1,
) {
  val isKnown: Boolean
    get() = lineNumber > 0 || className != null

  override fun toString(): String =
    when {
      fileName != null && lineNumber > 0 -> "$fileName:$lineNumber"
      className != null && methodName != null -> "$className.$methodName"
      className != null -> className
      else -> "<unknown>"
    }

  /** `Class.method(File.kt:line)` form, including every part that is known. */
  fun fullText(): String {
    val text = buildString {
      if (className != null) append(className)
      if (methodName != null) {
        if (isNotEmpty()) append('.')
        append(methodName)
      }
      if (fileName != null) {
        if (isNotEmpty()) append('(')
        append(fileName)
        if (lineNumber > 0) append(':').append(lineNumber)
        append(')')
      }
    }
    return text.ifEmpty { "<unknown>" }
  }

  companion object {
    @JvmField val UNKNOWN = SourceLocation()
  }
}

interface SourceLocator {
  /**
   * Captures the caller's position.
   *
   * @param skip number of extra user frames to skip, useful when wrapping the logger.
   */
  fun locate(skip: Int = 0): SourceLocation
}

object StackTraceSourceLocator : SourceLocator {
  override fun locate(skip: Int): SourceLocation {
    val stack = Thread.currentThread().stackTrace
    var remaining = skip
    for (element in stack) {
      val className = element.className
      if (isLoggerFrame(className)) continue
      if (remaining > 0) {
        remaining--
        continue
      }
      return SourceLocation(
        fileName = element.fileName,
        className = className,
        methodName = element.methodName,
        lineNumber = element.lineNumber,
      )
    }
    return SourceLocation.UNKNOWN
  }

  private fun isLoggerFrame(className: String): Boolean =
    className == "peroxicore.util.Logger" ||
      className == "peroxicore.util.DefaultLogger" ||
      className == "peroxicore.util.LogKt" ||
      className == "peroxicore.util.SourceLocationKt" ||
      className.startsWith("peroxicore.util.SourceLocator") ||
      className == "peroxicore.util.StackTraceSourceLocator" ||
      className == "peroxicore.util.NoopSourceLocator" ||
      className == "peroxicore.util.TraceKt" ||
      className == "java.lang.Thread"
}

object NoopSourceLocator : SourceLocator {
  override fun locate(skip: Int): SourceLocation = SourceLocation.UNKNOWN
}
