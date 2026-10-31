package peroxicore.ciallo

import java.io.*

/** Command line handling shared by [CialloMain] and peroxicore.Main. */
object CialloCli {
  fun run(args: Array<String>): Int {
    if (args.isEmpty()) {
      printUsage()
      return 2
    }

    return when (args[0]) {
      "run" -> runCommand(args.drop(1))
      "fmt", "format" -> formatCommand(args.drop(1))
      "console", "repl" -> consoleCommand(args.drop(1))
      "help", "--help", "-h" -> {
        printUsage()
        0
      }
      else -> runCommand(args.toList())
    }
  }

  private fun runCommand(args: List<String>): Int {
    var full = false
    var source: String? = null
    var sourceName = "<stdin>"
    val files = mutableListOf<String>()
    var index = 0

    while (index < args.size) {
      when (val arg = args[index]) {
        "--full" -> full = true
        "-e", "--eval" -> {
          if (index + 1 >= args.size) {
            System.err.println("error: " + arg + " needs a source string")
            return 2
          }
          source = args[index + 1]
          sourceName = "<eval>"
          index++
        }
        "-h", "--help" -> {
          printRunUsage()
          return 0
        }
        else -> files += arg
      }
      index++
    }

    if (source != null && files.isNotEmpty()) {
      System.err.println("error: use either -e or a file, not both")
      return 2
    }
    if (source == null && files.size > 1) {
      System.err.println("error: run accepts one file")
      return 2
    }

    val environment =
      CialloEnvironment(sandbox = if (full) CialloSandbox.FULL else CialloSandbox.SAFE)
    val engine = CialloEngine(environment)

    return try {
      when {
        source != null -> engine.execute(source, sourceName)
        files.size == 1 -> engine.executeFile(File(files[0]))
        else -> engine.execute(System.`in`.bufferedReader().readText(), "<stdin>")
      }
      0
    } catch (exception: CialloException) {
      System.err.println("ciallo: " + exception.message)
      1
    } catch (exception: IOException) {
      System.err.println("ciallo: " + exception.message)
      1
    }
  }

  private fun formatCommand(args: List<String>): Int {
    var write = false
    var check = false
    var indentSpaces = 2
    var source: String? = null
    var sourceName = "<stdin>"
    val files = mutableListOf<String>()
    var index = 0

    while (index < args.size) {
      when (val arg = args[index]) {
        "-w", "--write" -> write = true
        "-c", "--check" -> check = true
        "--indent" -> {
          if (index + 1 >= args.size) {
            System.err.println("error: --indent needs a number")
            return 2
          }
          val parsed = args[index + 1].toIntOrNull()
          if (parsed == null || parsed !in 1..8) {
            System.err.println("error: --indent must be between 1 and 8")
            return 2
          }
          indentSpaces = parsed
          index++
        }
        "-e", "--eval" -> {
          if (index + 1 >= args.size) {
            System.err.println("error: " + arg + " needs a source string")
            return 2
          }
          source = args[index + 1]
          sourceName = "<eval>"
          index++
        }
        "-h", "--help" -> {
          printFormatUsage()
          return 0
        }
        else -> files += arg
      }
      index++
    }

    if (write && check) {
      System.err.println("error: --write and --check are mutually exclusive")
      return 2
    }
    if (source != null && files.isNotEmpty()) {
      System.err.println("error: use either -e or a file, not both")
      return 2
    }
    if (write && source != null) {
      System.err.println("error: --write needs a file")
      return 2
    }

    val indentUnit = " ".repeat(indentSpaces)
    if (source != null) {
      return formatOne(source, sourceName, write = false, check = check, indentUnit = indentUnit)
    }
    if (files.isEmpty()) {
      return formatOne(System.`in`.bufferedReader().readText(), "<stdin>", write = false, check = check, indentUnit = indentUnit)
    }

    var result = 0
    for (file in files) {
      val formattedResult =
        try {
          formatOne(File(file).readText(), file, write = write, check = check, indentUnit = indentUnit)
        } catch (exception: IOException) {
          System.err.println("ciallo: " + exception.message)
          1
        }
      if (formattedResult != 0) result = formattedResult
    }
    return result
  }

  private fun formatOne(
    source: String,
    name: String,
    write: Boolean,
    check: Boolean,
    indentUnit: String,
  ): Int {
    val formatted =
      try {
        CialloFormatter.format(source, indentUnit)
      } catch (exception: CialloException) {
        System.err.println("ciallo: " + name + ": " + exception.message)
        return 1
      }

    if (write) {
      if (name == "<stdin>" || name == "<eval>") {
        System.err.println("ciallo: --write needs a file")
        return 2
      }
      File(name).writeText(formatted)
      return 0
    }

    if (check) {
      if (formatted == source) return 0
      System.err.println("ciallo: " + name + " is not formatted")
      return 1
    }

    print(formatted)
    return 0
  }

  private fun consoleCommand(args: List<String>): Int {
    var full = false
    for (arg in args) {
      when (arg) {
        "--full" -> full = true
        "-h", "--help" -> {
          printConsoleUsage()
          return 0
        }
        else -> {
          System.err.println("error: unknown console option " + arg)
          return 2
        }
      }
    }

    val environment =
      CialloEnvironment(sandbox = if (full) CialloSandbox.FULL else CialloSandbox.SAFE)

    return try {
      // Keep JLine optional in the embedded mod jar: the standalone shadow jar
      // contains it, while the Android embed artifact intentionally excludes it.
      val consoleClass = Class.forName("peroxicore.ciallo.CialloConsole")
      val constructor = consoleClass.getConstructor(CialloEnvironment::class.java)
      val console = constructor.newInstance(environment)
      consoleClass.getMethod("run").invoke(console) as Int
    } catch (_: ClassNotFoundException) {
      System.err.println("ciallo: console is not available in this jar")
      2
    } catch (exception: LinkageError) {
      System.err.println("ciallo: console is not available in this jar: " + exception.message)
      2
    } catch (exception: Exception) {
      System.err.println("ciallo: console failed: " + exception.message)
      1
    }
  }

  private fun printUsage() {
    System.err.println("Usage: java -jar <jar> ciallo <command> ...")
    System.err.println()
    System.err.println("Commands:")
    System.err.println("  run      Execute a Ciallo script.")
    System.err.println("  fmt      Format a Ciallo script (alias: format).")
    System.err.println("  console  Open an interactive console (alias: repl).")
    System.err.println("  help     Show this help.")
    System.err.println()
    printRunUsage()
    printFormatUsage()
    printConsoleUsage()
  }

  private fun printRunUsage() {
    System.err.println("run usage:")
    System.err.println("  ciallo run [--full] <file>")
    System.err.println("  ciallo run [--full] -e <source>")
    System.err.println("  ciallo run [--full]              (read stdin)")
  }

  private fun printFormatUsage() {
    System.err.println("fmt usage:")
    System.err.println("  ciallo fmt [-w|--write] [-c|--check] [--indent <n>] <file>")
    System.err.println("  ciallo fmt [-c|--check] [--indent <n>] -e <source>")
    System.err.println("  ciallo fmt [-c|--check] [--indent <n>]              (read stdin)")
  }

  private fun printConsoleUsage() {
    System.err.println("console usage:")
    System.err.println("  ciallo console [--full]")
    System.err.println("  ciallo repl [--full]")
  }
}
