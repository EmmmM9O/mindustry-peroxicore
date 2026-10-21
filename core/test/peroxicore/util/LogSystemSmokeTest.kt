package peroxicore.util

import kotlin.test.*

class LogSystemSmokeTest {
  @Test
  fun disablesBeforeComputingMessage() {
    var computed = false
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.INFO
        formatter = { "" }
        appender = { _ -> }
      }
    val logger = DefaultLogger("test", config)

    logger.debug {
      computed = true
      "hidden"
    }

    assertFalse(computed)
  }

  @Test
  fun printsCallerLocationOnDemand() {
    val lines = mutableListOf<String>()
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.INFO
        formatter = { "$level|$message|$location" }
        appender = { text -> lines += text }
      }
    val logger = DefaultLogger("test", config)

    logger.info { "plain" }
    logger.info(location = true) { "located" }

    assertEquals("Info|plain|<unknown>", lines[0])
    assertTrue(lines[1].contains("LogSystemSmokeTest.kt"), lines[1])
  }

  @Test
  fun printsLocationByDefaultFromConfig() {
    val lines = mutableListOf<String>()
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.INFO
        printLocation = true
        formatter = { "$location" }
        appender = { text -> lines += text }
      }
    val logger = DefaultLogger("test", config)

    logger.info { "located" }

    assertTrue(lines.single().contains("LogSystemSmokeTest.kt"), lines.single())
  }

  @Test
  fun throttlesRepeatedCallsPerCallSite() {
    var emitted = 0
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.TRACE
        throttlePeriodMillis = 200
        formatter = { "" }
        appender = { _ -> emitted++ }
      }
    val logger = DefaultLogger("test", config)

    repeat(10) { logger.infoThrottled { "noise" } }
    assertEquals(1, emitted)

    Thread.sleep(250)
    logger.infoThrottled { "noise" }
    assertEquals(2, emitted)
  }

  @Test
  fun separateCallSitesThrottleIndependently() {
    var emitted = 0
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.TRACE
        throttlePeriodMillis = 1_000
        formatter = { "" }
        appender = { _ -> emitted++ }
      }
    val logger = DefaultLogger("test", config)

    repeat(3) { logger.infoThrottled { "first" } }
    repeat(3) { logger.infoThrottled { "second" } }

    assertEquals(2, emitted)
  }

  @Test
  fun dslSupportsLocationAndThrottle() {
    val lines = mutableListOf<String>()
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.TRACE
        throttlePeriodMillis = 1_000
        formatter = { "$message|$location" }
        appender = { text -> lines += text }
      }
    val logger = DefaultLogger("test", config)

    repeat(5) {
      logger.atInfo {
        message("noise")
        location()
        throttle()
      }
    }

    assertEquals(1, lines.size)
    assertTrue(lines.single().contains("LogSystemSmokeTest.kt"), lines.single())
  }

  @Test
  fun disabledDslDoesNotRunBlock() {
    var ran = false
    val config =
      LoggerConfig().apply {
        minimumLevel = LogLevel.INFO
        formatter = { "" }
        appender = { _ -> }
      }
    val logger = DefaultLogger("test", config)

    logger.atDebug {
      ran = true
      message("hidden")
    }

    assertFalse(ran)
  }
}
