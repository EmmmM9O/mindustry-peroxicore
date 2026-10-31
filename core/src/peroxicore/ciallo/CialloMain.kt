package peroxicore.ciallo

import kotlin.system.exitProcess

/** Standalone entry point for the Ciallo CLI. */
object CialloMain {
  @JvmStatic
  fun main(args: Array<String>) {
    val code = CialloCli.run(args)
    if (code != 0) exitProcess(code)
  }
}
