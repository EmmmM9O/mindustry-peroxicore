package peroxicore.ciallo

/** Base exception for all Ciallo language failures. */
open class CialloException(
  message: String,
  val line: Int = -1,
  val column: Int = -1,
  cause: Throwable? = null,
) : RuntimeException(
  if (line > 0) "$message (line $line, column ${column + 1})" else message,
  cause,
)

class CialloSyntaxException(
  message: String,
  line: Int,
  column: Int,
  cause: Throwable? = null,
) : CialloException(message, line, column, cause)

class CialloRuntimeException(
  message: String,
  line: Int = -1,
  column: Int = -1,
  cause: Throwable? = null,
) : CialloException(message, line, column, cause)

class CialloSandboxException(
  message: String,
) : CialloException("sandbox violation: $message")
