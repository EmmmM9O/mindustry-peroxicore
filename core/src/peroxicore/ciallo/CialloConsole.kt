package peroxicore.ciallo

import org.jline.reader.*
import org.jline.terminal.*
import java.io.IOException

/** A small interactive Ciallo console with history, completion and highlighting. */
class CialloConsole(
  val environment: CialloEnvironment = CialloEnvironment(),
) {
  fun run(): Int {
    val terminal =
      try {
        TerminalBuilder.builder().system(true).build()
      } catch (exception: IOException) {
        System.err.println("ciallo: cannot start console: " + exception.message)
        return 1
      }

    return try {
      val engine = CialloEngine(environment)
      val reader =
        LineReaderBuilder.builder()
          .terminal(terminal)
          .appName("ciallo")
          .completer(CialloCompleter(environment, engine.globalScope))
          .highlighter(CialloHighlighter(environment))
          .build()

      println("Ciallo console. Type :help for commands, :quit to exit.")

      val buffer = StringBuilder()
      var continuationIndent = 0

      while (true) {
        val prompt =
          if (buffer.length == 0) {
            "ciallo> "
          } else {
            " ".repeat(continuationIndent) + "... "
          }

        val line =
          try {
            reader.readLine(prompt)
          } catch (_: UserInterruptException) {
            buffer.setLength(0)
            continuationIndent = 0
            println()
            continue
          } catch (_: EndOfFileException) {
            break
          }

        if (buffer.length == 0 && line.trimStart().startsWith(":")) {
          when (line.trim()) {
            ":quit", ":q", ":exit" -> return 0
            ":help", ":h" -> printHelp()
            ":reset" -> {
              buffer.setLength(0)
              continuationIndent = 0
              println("input buffer cleared")
            }
            ":env" -> printEnvironment()
            else -> println("unknown console command: " + line.trim())
          }
          continue
        }

        if (buffer.length == 0 && line.isBlank()) continue

        if (buffer.length > 0) buffer.append('\n')
        buffer.append(line)

        val balance = scanCialloSource(buffer.toString())
        if (balance.hasOpen) {
          continuationIndent = balance.indentation
          continue
        }

        val source = buffer.toString()
        buffer.setLength(0)
        continuationIndent = 0
        execute(engine, source)
      }

      0
    } finally {
      terminal.close()
    }
  }

  private fun execute(engine: CialloEngine, source: String) {
    try {
      val result = engine.execute(source, "<console>")
      if (
        result !is CialloValue.Nil
      ) {
        println(result.format())
      }
    } catch (exception: CialloException) {
      System.err.println("ciallo: " + exception.message)
    } catch (exception: Exception) {
      System.err.println("ciallo: " + exception)
    }
  }

  private fun printHelp() {
    println(":help   show this help")
    println(":quit   leave the console")
    println(":reset  clear the current input buffer")
    println(":env    show the sandbox and mounted globals")
  }

  private fun printEnvironment() {
    println("sandbox: " + environment.sandbox)
    val globals = environment.globalScope.localValues()
    if (globals.isEmpty()) {
      println("globals: <none>")
    } else {
      println("globals:")
      for ((name, value) in globals) {
        println("  " + name + " = " + value.format())
      }
    }
  }
}

internal data class CialloSourceBalance(
  val braces: Int,
  val parens: Int,
  val brackets: Int,
) {
  val hasOpen: Boolean get() = braces > 0 || parens > 0 || brackets > 0

  val indentation: Int get() = maxOf(0, braces) * 2
}

/** Scans source only for delimiter balance, ignoring strings and comments. */
internal fun scanCialloSource(source: String): CialloSourceBalance {
  var braces = 0
  var parens = 0
  var brackets = 0
  var index = 0
  var mode = 0 // 0 normal, 1 line comment, 2 block comment, 3 string, 4 long string

  while (index < source.length) {
    val char = source[index]
    when (mode) {
      1 -> {
        if (char == '\n') mode = 0
      }
      2 -> {
        if (char == '*' && index + 1 < source.length && source[index + 1] == '/') {
          mode = 0
          index++
        }
      }
      3 -> {
        when {
          char == '\\' -> index++
          char == '"' -> mode = 0
        }
      }
      4 -> {
        if (
          char == '"' &&
            index + 2 < source.length &&
            source[index + 1] == '"' &&
            source[index + 2] == '"'
        ) {
          mode = 0
          index += 2
        }
      }
      else -> {
        when {
          char == '/' && index + 1 < source.length && source[index + 1] == '/' -> {
            mode = 1
            index++
          }
          char == '/' && index + 1 < source.length && source[index + 1] == '*' -> {
            mode = 2
            index++
          }
          char == '"' &&
            index + 2 < source.length &&
            source[index + 1] == '"' &&
            source[index + 2] == '"' -> {
            mode = 4
            index += 2
          }
          char == '"' -> mode = 3
          char == '{' -> braces++
          char == '}' -> braces--
          char == '(' -> parens++
          char == ')' -> parens--
          char == '[' -> brackets++
          char == ']' -> brackets--
        }
      }
    }
    index++
  }

  return CialloSourceBalance(braces, parens, brackets)
}
