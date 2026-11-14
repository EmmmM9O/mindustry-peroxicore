package peroxicore.ciallo

import peroxicore.annotations.*
import peroxicore.ciallo.value.*

/**
 * A host component that reacts to the values a script produces.
 *
 * It is an ordinary interface whose default implementations do nothing, so a
 * host composes whatever it needs.  Entries are attached with [MethodEntry]:
 *
 * ```
 * class LoggingSink : CialloOutputComp {
 *   @MethodEntry(entryMethod = "printed", context = ["text"], insert = InsertPosition.END)
 *   fun log(text: String) = println("[script] " + text)
 * }
 *
 * val environment = CialloEnvironment(outputComp = LoggingSink())
 * ```
 *
 * `HEAD` entries see the arguments before the host body runs, and `INJECT`
 * entries wrap the result; `context` reads a property of the owning class.
 */
interface CialloOutputComp {
  /** Every line a script prints. */
  fun printed(text: String) {}

  /** The result of a top level script run. */
  fun finished(result: CialloValue) {}
}
