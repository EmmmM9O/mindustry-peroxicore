package peroxicore.util

import java.util.concurrent.*
import kotlin.reflect.*
import kotlinx.datetime.*

@DslMarker annotation class LogDsl

enum class LogLevel(val severity: Int, val label: String) {
  TRACE(0, "Trace"),
  DEBUG(10, "Debug"),
  INFO(20, "Info"),
  WARN(30, "Warn"),
  ERROR(40, "Error"),
  FATAL(50, "Fatal");

  override fun toString(): String = label
}

fun interface LogMark {
  fun name(): String
}

data class StringMark(val name: String) : LogMark {
  override fun name(): String = name
}

typealias LogFormatter = LogEvent.() -> String

typealias Appender = LogEvent.(text: String) -> Unit

open class LogEvent(
  val level: LogLevel,
  val mark: LogMark?,
  val message: String?,
  val cause: Throwable?,
  val data: Any?,
  val logger: Logger,
  val time: LocalDateTime,
  val location: SourceLocation,
)

open class LogEventBuilder(
  var level: LogLevel = LogLevel.TRACE,
  var mark: LogMark? = null,
  var message: String? = null,
  var cause: Throwable? = null,
  var data: Any? = null,
) {
  /** Print this event's [SourceLocation] even when the logger is configured not to. */
  var printLocation: Boolean = false

  var skip: Int = 0

  var skipFunc: ((String)->Boolean)? = null

  /**
   * Suppress repeated events from the same call-site within [LoggerConfig.throttlePeriodMillis].
   */
  var throttled: Boolean = false

  /** Optional custom throttle key; defaults to the captured [SourceLocation]. */
  var throttleKey: Any? = null
}

@LogDsl
class LogEventDsl : LogEventBuilder() {
  fun level(level: LogLevel) {
    this.level = level
  }

  fun mark(mark: LogMark) {
    this.mark = mark
  }

  fun mark(name: String) {
    this.mark = StringMark(name)
  }

  fun message(message: Any?) {
    this.message = message?.toString()
  }

  fun cause(cause: Throwable?) {
    this.cause = cause
  }

  fun data(data: Any?) {
    this.data = data
  }

  @JvmName("enableLocation")
  fun location(enabled: Boolean = true) {
    this.printLocation = enabled
  }

  fun location(skip: Int) {
    this.printLocation = true
    this.skip = skip
  }

  fun skipIf(func: (String) -> Boolean) {
    this.skipFunc = func
  }

  fun skip(kClass: KClass<*>) {
    this.skipFunc = { it == kClass.qualifiedName }
  }

  fun throttle(key: Any? = null) {
    this.throttled = true
    this.throttleKey = key
  }

  fun build(): LogEventBuilder {
    require(message != null) { "message must be set" }
    return this
  }
}

fun interface ErrorFormatter {
  fun format(error: Throwable): String
}

object DefaultErrorFormatter : ErrorFormatter {
  override fun format(error: Throwable): String = "failed $error"
}

open class LoggerConfig(
  var errorFormatter: ErrorFormatter = DefaultErrorFormatter,
  var minimumLevel: LogLevel = LogLevel.INFO,
  var formatter: LogFormatter = DefaultFormatter,
  var appender: Appender = DefaultAppender,
  var printLocation: Boolean = false,
  var sourceLocator: SourceLocator = StackTraceSourceLocator,
  var throttlePeriodMillis: Long = 1000L,
)

abstract class Logger {
  abstract val name: String?
  abstract val config: LoggerConfig

  private val throttleStates = ConcurrentHashMap<Any, Long>()

  fun isEnabled(level: LogLevel): Boolean = level.severity >= config.minimumLevel.severity

  fun loggingEnabled(level: LogLevel, mark: LogMark? = null): Boolean = isEnabled(level)

  fun traceEnabled(mark: LogMark? = null): Boolean = isEnabled(LogLevel.TRACE)

  fun debugEnabled(mark: LogMark? = null): Boolean = isEnabled(LogLevel.DEBUG)

  fun infoEnabled(mark: LogMark? = null): Boolean = isEnabled(LogLevel.INFO)

  fun warnEnabled(mark: LogMark? = null): Boolean = isEnabled(LogLevel.WARN)

  fun errorEnabled(mark: LogMark? = null): Boolean = isEnabled(LogLevel.ERROR)

  @PublishedApi
  internal fun shouldLog(key: Any): Boolean {
    val periodMillis = config.throttlePeriodMillis
    if (periodMillis <= 0L) return true
    val periodNanos = periodMillis * 1_000_000L
    val now = System.nanoTime()
    while (true) {
      val last = throttleStates[key]
      if (last != null && now - last < periodNanos) return false
      if (last == null) {
        if (throttleStates.putIfAbsent(key, now) == null) return true
      } else if (throttleStates.replace(key, last, now)) {
        return true
      }
    }
  }

  @PublishedApi
  internal inline fun emit(
    level: LogLevel,
    cause: Throwable? = null,
    mark: LogMark? = null,
    printLocation: Boolean = false,
    throttled: Boolean = false,
    data: Any? = null,
    throttleKey: Any? = null,
    message: () -> Any?,
  ) {
    val loggerConfig = config
    val displayLocation = printLocation || loggerConfig.printLocation
    val location =
      if (displayLocation || throttled) loggerConfig.sourceLocator.locate()
      else SourceLocation.UNKNOWN
    if (throttled && !shouldLog(throttleKey ?: location)) return
    val text =
      try {
        message()?.toString() ?: ""
      } catch (error: Throwable) {
        loggerConfig.errorFormatter.format(error)
      }
    val event =
      LogEvent(
        level = level,
        mark = mark,
        message = text,
        cause = cause,
        data = data,
        logger = this,
        time = timeNow(),
        location = if (displayLocation) location else SourceLocation.UNKNOWN,
      )
    loggerConfig.appender(event, loggerConfig.formatter(event))
  }

  @PublishedApi
  internal fun emitEvent(builder: LogEventBuilder) {
    if (!isEnabled(builder.level)) return
    val loggerConfig = config
    val displayLocation = builder.printLocation || loggerConfig.printLocation
    val location =
      if (displayLocation || builder.throttled) loggerConfig.sourceLocator.locate(builder.skip, builder.skipFunc)
      else SourceLocation.UNKNOWN
    if (builder.throttled && !shouldLog(builder.throttleKey ?: location)) return
    val event =
      LogEvent(
        level = builder.level,
        mark = builder.mark,
        message = builder.message ?: "",
        cause = builder.cause,
        data = builder.data,
        logger = this,
        time = timeNow(),
        location = if (displayLocation) location else SourceLocation.UNKNOWN,
      )
    loggerConfig.appender(event, loggerConfig.formatter(event))
  }

  inline fun at(block: LogEventDsl.() -> Unit) {
    emitEvent(LogEventDsl().apply(block).build())
  }

  inline fun at(level: LogLevel, block: LogEventDsl.() -> Unit) {
    if (!isEnabled(level)) return
    emitEvent(
      LogEventDsl()
        .apply {
          this.level = level
          block()
        }
        .build()
    )
  }

  fun at(builder: LogEventBuilder) {
    emitEvent(builder)
  }

  inline fun trace(location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.TRACE)) return
    emit(
      level = LogLevel.TRACE,
      printLocation = location,
      message = message,
    )
  }

  inline fun debug(location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.DEBUG)) return
    emit(
      level = LogLevel.DEBUG,
      printLocation = location,
      message = message,
    )
  }

  inline fun info(location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.INFO)) return
    emit(
      level = LogLevel.INFO,
      printLocation = location,
      message = message,
    )
  }

  inline fun warn(location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.WARN)) return
    emit(
      level = LogLevel.WARN,
      printLocation = location,
      message = message,
    )
  }

  inline fun error(location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.ERROR)) return
    emit(
      level = LogLevel.ERROR,
      printLocation = location,
      message = message,
    )
  }

  inline fun fatal(location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.FATAL)) return
    emit(
      level = LogLevel.FATAL,
      printLocation = location,
      message = message,
    )
  }

  inline fun error(cause: Throwable?, location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.ERROR)) return
    emit(
      level = LogLevel.ERROR,
      cause = cause,
      printLocation = location,
      message = message,
    )
  }

  inline fun fatal(cause: Throwable?, location: Boolean = false, message: () -> Any?) {
    if (!isEnabled(LogLevel.FATAL)) return
    emit(
      level = LogLevel.FATAL,
      cause = cause,
      printLocation = location,
      message = message,
    )
  }

  /** Logs at [level], but only once per [LoggerConfig.throttlePeriodMillis] per call-site. */
  inline fun throttled(
    level: LogLevel,
    cause: Throwable? = null,
    location: Boolean = false,
    message: () -> Any?,
  ) {
    if (!isEnabled(level)) return
    emit(
      level = level,
      cause = cause,
      printLocation = location,
      throttled = true,
      message = message,
    )
  }

  inline fun traceThrottled(location: Boolean = false, message: () -> Any?) =
    throttled(LogLevel.TRACE, location = location, message = message)

  inline fun debugThrottled(location: Boolean = false, message: () -> Any?) =
    throttled(LogLevel.DEBUG, location = location, message = message)

  inline fun infoThrottled(location: Boolean = false, message: () -> Any?) =
    throttled(LogLevel.INFO, location = location, message = message)

  inline fun warnThrottled(location: Boolean = false, message: () -> Any?) =
    throttled(LogLevel.WARN, location = location, message = message)

  inline fun errorThrottled(location: Boolean = false, message: () -> Any?) =
    throttled(LogLevel.ERROR, location = location, message = message)

  inline fun fatalThrottled(location: Boolean = false, message: () -> Any?) =
    throttled(LogLevel.FATAL, location = location, message = message)

  inline fun atTrace(block: LogEventDsl.() -> Unit): Unit = at(LogLevel.TRACE, block)

  inline fun atDebug(block: LogEventDsl.() -> Unit): Unit = at(LogLevel.DEBUG, block)

  inline fun atInfo(block: LogEventDsl.() -> Unit): Unit = at(LogLevel.INFO, block)

  inline fun atWarn(block: LogEventDsl.() -> Unit): Unit = at(LogLevel.WARN, block)

  inline fun atError(block: LogEventDsl.() -> Unit): Unit = at(LogLevel.ERROR, block)

  inline fun atFatal(block: LogEventDsl.() -> Unit): Unit = at(LogLevel.FATAL, block)

  fun entry(vararg arguments: Any?): Unit = trace { "entry(${arguments.joinToString()})" }
}

class DefaultLogger(override val name: String?, override val config: LoggerConfig) : Logger()

interface NameResolver {
  fun name(func: () -> Unit): String

  fun name(clazz: KClass<*>): String
}

@LogDsl
class NameResolverDsl : NameResolver {
  private lateinit var functionResolver: (func: () -> Unit) -> String
  private lateinit var classResolver: (clazz: KClass<*>) -> String

  override fun name(func: () -> Unit): String = functionResolver(func)

  override fun name(clazz: KClass<*>): String = classResolver(clazz)

  fun functionName(resolver: (func: () -> Unit) -> String) {
    functionResolver = resolver
  }

  fun className(resolver: (clazz: KClass<*>) -> String) {
    classResolver = resolver
  }

  fun build(): NameResolver {
    require(this::functionResolver.isInitialized) { "Must set functionName" }
    require(this::classResolver.isInitialized) { "Must set className" }
    return this
  }
}

object DefaultNameResolver : NameResolver {
  override fun name(func: () -> Unit): String =
    when (func) {
      is KFunction<*> -> func.name
      else -> func::class.simpleName.toString()
    }

  override fun name(clazz: KClass<*>): String = clazz.java.name.toCleanClassName()

  private val classNameEndings = listOf("Kt$", "$")

  private fun String.toCleanClassName(): String {
    classNameEndings.forEach { ending ->
      val indexOfEnding = this.indexOf(ending)
      if (indexOfEnding != -1) {
        return this.substring(0, indexOfEnding)
      }
    }
    return this
  }
}

fun interface LoggerFactory {
  fun logger(name: String?, config: LoggerConfig): Logger
}

object DefaultLoggerFactory : LoggerFactory {
  override fun logger(name: String?, config: LoggerConfig): Logger = DefaultLogger(name, config)
}

@LogDsl
open class LoggerConfigDslBase : LoggerConfig() {
  fun errorFormatter(errorFormatter: ErrorFormatter) {
    this.errorFormatter = errorFormatter
  }

  fun minimumLevel(level: LogLevel) {
    this.minimumLevel = level
  }

  fun formatter(formatter: LogFormatter) {
    this.formatter = formatter
  }

  fun appender(appender: Appender) {
    this.appender = appender
  }

  fun printLocation(enabled: Boolean = true) {
    this.printLocation = enabled

  }

  fun sourceLocator(locator: SourceLocator) {
    this.sourceLocator = locator
  }

  fun throttlePeriod(millis: Long) {
    this.throttlePeriodMillis = millis
  }

  fun build(): LoggerConfig = this
}

object Logging {
  @LogDsl
  class LoggerConfigDsl(private val resolver: NameResolver) : LoggerConfigDslBase() {
    var name: String? = null

    fun name(name: String?) {
      this.name = name
    }

    fun name(func: () -> Unit) {
      this.name = resolver.name(func)
    }

    fun name(clazz: KClass<*>) {
      this.name = resolver.name(clazz)
    }
  }

  @Volatile var factory: LoggerFactory? = DefaultLoggerFactory
  @Volatile var nameResolver: NameResolver = DefaultNameResolver

  fun logger(block: LoggerConfigDsl.() -> Unit): Logger =
    LoggerConfigDsl(nameResolver).apply(block).let {
      (factory ?: DefaultLoggerFactory).logger(it.name, it.build())
    }
}

@LogDsl
class LoggingConfigDsl {
  fun factory(factory: LoggerFactory) {
    Logging.factory = factory
  }

  fun namer(block: NameResolverDsl.() -> Unit) {
    Logging.nameResolver = NameResolverDsl().apply(block).build()
  }

  fun check(): LoggingConfigDsl {
    require(Logging.factory != null) { "factory can not be null" }
    return this
  }
}

fun logConfig(block: LoggingConfigDsl.() -> Unit): Logging =
  LoggingConfigDsl().apply(block).let { Logging }
