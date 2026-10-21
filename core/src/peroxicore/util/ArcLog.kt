package peroxicore.util

import arc.util.*
import kotlin.reflect.*

val ArcLogAppender: Appender = { text ->
  when (level) {
    LogLevel.TRACE -> Log.info("Trace:$text")
    LogLevel.DEBUG -> Log.debug(text)
    LogLevel.INFO -> Log.info(text)
    LogLevel.WARN -> Log.warn(text)
    LogLevel.ERROR -> Log.err(text)
    LogLevel.FATAL -> Log.err("Fatal:$text")
  }
}

fun LogLevel.toArcLevel(): Log.LogLevel =
  when (this) {
    LogLevel.TRACE -> Log.LogLevel.none
    LogLevel.DEBUG -> Log.LogLevel.debug
    LogLevel.INFO -> Log.LogLevel.info
    LogLevel.WARN -> Log.LogLevel.warn
    LogLevel.ERROR -> Log.LogLevel.err
    LogLevel.FATAL -> Log.LogLevel.err
  }

fun Log.LogLevel.toLogLevel(): LogLevel =
  when (this) {
    Log.LogLevel.none -> LogLevel.TRACE
    Log.LogLevel.info -> LogLevel.INFO
    Log.LogLevel.debug -> LogLevel.DEBUG
    Log.LogLevel.warn -> LogLevel.WARN
    Log.LogLevel.err -> LogLevel.ERROR
  }

fun LoggerConfig.arcAppender() {
  appender = ArcLogAppender
}

class ArcLogHandlerAppender {
  var origin: Log.LogHandler = Log.logger

  fun build(): Appender = { text ->
    val prefix = if (level == LogLevel.FATAL) "FATAL" else ""
    origin.log(level.toArcLevel(), prefix + text)
  }
}

fun LoggerConfig.arcHandlerAppender() {
  appender = ArcLogHandlerAppender().build()
}

object Marks {
  val log = StringMark("Log")

  fun processor(clazz: KClass<*>) = StringMark("Processor ${clazz.simpleName}")
}
