package peroxicore.util

fun <T : Any> T?.workOrEmpty(func: (value: T) -> String) = if (this != null) func(this) else ""

fun <T : Any> T?.formatOrEmpty(func: (text: String) -> String) =
  if (this != null) func(this.toString()) else ""

tailrec fun throwableMsg(throwable: Throwable?, msg: String = ""): String =
  if (throwable == null || throwable.cause == throwable) {
    msg
  } else {
    throwableMsg(throwable.cause, "$msg, Caused by: '${throwable.message}'\n")
  }
