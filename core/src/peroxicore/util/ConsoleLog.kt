package peroxicore.util

val NoopAppender: Appender = {}

private fun LogLevel.ansiColor(): String =
  when (this) {
    LogLevel.TRACE -> "\u001B[2m"
    LogLevel.DEBUG -> "\u001B[3m"
    LogLevel.INFO -> "\u001B[34;1m"
    LogLevel.WARN -> "\u001B[33;1m"
    LogLevel.ERROR -> "\u001B[31;1m"
    LogLevel.FATAL -> ""
  }

val DefaultAppender: Appender = { text ->
  val color = level.ansiColor()
  if (color.isEmpty()) println(text) else println("$color$text\u001B[0m")
}

private val defaultTimeText: TimeFormatter = timeFormat {
  chars("T")
  hour()
  chars(":")
  minute()
  chars(":")
  second()
}

val DefaultFormatter: LogFormatter = {
  buildString {
    append(defaultTimeText(time))
    mark?.name()?.let { append('[').append(it).append(']') }
    logger.name?.let { append('[').append(it).append(']') }
    append('[').append(level.label).append("]:").append(message.orEmpty())
    if (location.isKnown) append(" (").append(location).append(')')
    cause?.let { appendCause(it) }
  }
}

private fun StringBuilder.appendCause(cause: Throwable) {
  var current: Throwable? = cause
  var depth = 0
  while (current != null && depth++ < 16) {
    append("\nCaused by: ").append(current.message ?: current.javaClass.simpleName)
    val next = current.cause
    if (next === current) break
    current = next
  }
}

fun LoggerConfig.defaultError() {
  errorFormatter = DefaultErrorFormatter
}

fun LoggerConfig.defaultFormatter() {
  formatter = DefaultFormatter
}

fun LoggerConfig.noopAppender() {
  appender = NoopAppender
}

fun LoggerConfig.defaultAppender() {
  appender = DefaultAppender
}

@LogDsl
class SimpleFormatter {
  class Event(event: LogEvent, private val owner: SimpleFormatter) :
    LogEvent(
      event.level,
      event.mark,
      event.message,
      event.cause,
      event.data,
      event.logger,
      event.time,
      event.location,
    ) {
    val timeText: String by lazy(LazyThreadSafetyMode.NONE) { owner.timeTextFormatter(time) }
    val levelColor: String by lazy(LazyThreadSafetyMode.NONE) { owner.colorizer(level) }
  }

  private lateinit var templateFactory: Event.() -> String
  private lateinit var timeTextFormatter: TimeFormatter
  private lateinit var colorizer: LogLevel.() -> String

  fun template(template: Event.() -> String) {
    templateFactory = template
  }

  fun poTime(formatter: TimeDataFormatter) {
    timeTextFormatter = timePOFormat(formatter)
  }

  fun time(formatter: XTimeFormatter) {
    timeTextFormatter = timeFormat(formatter)
  }

  fun levelColor(color: LogLevel.() -> String) {
    colorizer = color
  }

  fun build(): LogFormatter {
    require(this::templateFactory.isInitialized) { "must set template" }
    if (!this::timeTextFormatter.isInitialized) timeTextFormatter = defaultTimeText
    if (!this::colorizer.isInitialized) colorizer = { ansiColor() }
    val owner = this
    return { templateFactory(Event(this, owner)) }
  }

  fun asFormatter(formatter: LogFormatter) = formatter
}

fun LoggerConfig.simpleFormat(block: SimpleFormatter.() -> Unit) {
  formatter = SimpleFormatter().apply(block).build()
}
