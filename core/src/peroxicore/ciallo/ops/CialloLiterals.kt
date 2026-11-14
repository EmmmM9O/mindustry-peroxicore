package peroxicore.ciallo.ops

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/** Parses time literals such as `5s`, `1.5m` and `2h`. */
object CialloTimeLiterals : CialloNumberSuffixParser {
  override val label: String get() = "time literals"

  override fun parse(token: String): CialloValue? {
    if (token.isEmpty()) return null
    val unit =
      when (token.last()) {
        's' -> CialloTimeUnit.Second
        'm' -> CialloTimeUnit.Minute
        'h' -> CialloTimeUnit.Hour
        else -> return null
      }
    val amount = token.dropLast(1).toDoubleOrNull() ?: return null
    return CialloTime(amount, unit)
  }
}
