@file:OptIn(ExperimentalTime::class)

package peroxicore.util

import kotlin.time.*
import kotlin.time.Clock
import kotlinx.datetime.*
import kotlinx.datetime.format.*

typealias XTimeFormatter = DateTimeFormatBuilder.WithDateTime.() -> Unit

fun timeNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

data class TimeFormatData(
  val yyyy: Int,
  val yy: Int,
  val MM: Int,
  val dd: Int,
  val HH: Int,
  val mm: Int,
  val ss: Int,
)

typealias TimeDataFormatter = TimeFormatData.() -> String

fun LocalDateTime.poFormat(formatter: TimeDataFormatter) =
  formatter(
    TimeFormatData(
      yyyy = year,
      yy = year % 100,
      MM = month.ordinal,
      dd = day,
      HH = hour,
      mm = minute,
      ss = second,
    )
  )

typealias TimeFormatter = LocalDateTime.() -> String

fun timePOFormat(formatter: TimeDataFormatter): TimeFormatter = { time: LocalDateTime ->
  time.poFormat(formatter)
}

fun timeFromFormat(formatter: DateTimeFormat<LocalDateTime>): TimeFormatter =
  { time: LocalDateTime ->
    time.format(formatter)
  }

fun timeFormat(formatter: XTimeFormatter): TimeFormatter = { time: LocalDateTime ->
  time.format(LocalDateTime.Format(formatter))
}
