package peroxicore

import peroxicore.ciallo.*
import kotlin.system.exitProcess

/** Entry point for running the built jar directly. */
object Main {
  @JvmStatic
  fun main(args: Array<String>) {
    val code = dispatch(args)
    if (code != 0) exitProcess(code)
  }

  private fun dispatch(args: Array<String>): Int =
    when (args.firstOrNull()) {
      "ciallo" -> CialloCli.run(args.drop(1).toTypedArray())
      "help", "--help", "-h" -> {
        printUsage()
        0
      }
      null -> {
        printUsage()
        2
      }
      else -> {
        System.err.println("unknown command: " + args[0])
        printUsage()
        2
      }
    }

  private fun printUsage() {
    System.err.println("Usage: java -jar <jar> ciallo <run|fmt|console|help> ...")
    System.err.println("       java -jar <jar> ciallo --help")
  }
}
