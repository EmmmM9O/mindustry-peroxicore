package peroxicore.ciallo

import org.jline.reader.*

/** Tab completion for functions, globals and simple object fields. */
internal class CialloCompleter(
  private val environment: CialloEnvironment,
  private val globalScope: CialloScope,
) : Completer {
  override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
    for (candidate in candidatesFor(line.word())) {
      candidates += Candidate(candidate)
    }
  }

  fun candidatesFor(word: String): List<String> {
    val result = linkedSetOf<String>()

    if (word.startsWith(":")) {
      consoleCommands.filter { it.startsWith(word) }.forEach { result += it }
      return result.toList()
    }

    val at = word.startsWith("@")
    val raw = if (at) word.substring(1) else word
    val lastDot = raw.lastIndexOf('.')

    if (lastDot >= 0) {
      val prefix = raw.substring(0, lastDot + 1)
      val member = raw.substring(lastDot + 1)
      val target = resolvePath(raw.substring(0, lastDot))
      if (target is CialloValue.Obj) {
        target.fields.keys.filter { it.startsWith(member) }.forEach {
          result += form(at, prefix + it)
        }
      }
      commonMethods.filter { it.startsWith(member) }.forEach {
        result += form(at, prefix + it)
      }
      if (at) {
        environment.symbols.keys.filter { it.startsWith(raw) }.forEach {
          result += "@" + it
        }
      }
      return result.toList()
    }

    val names = linkedSetOf<String>()
    if (at) {
      names += globalScope.localValues().keys
      names += environment.symbols.keys.filter { !it.contains('.') }
    } else {
      names += environment.natives.keys
      names += globalScope.localValues().keys
      names += environment.symbols.keys.filter { !it.contains('.') }
    }
    names.filter { it.startsWith(raw) }.forEach { result += form(at, it) }

    if (at) {
      environment.symbols.keys.filter { it.startsWith(raw) }.forEach {
        result += "@" + it
      }
    }
    return result.toList()
  }

  private fun form(at: Boolean, value: String): String = if (at) "@" + value else value

  private fun resolvePath(path: String): CialloValue? {
    if (path.isEmpty()) return null
    val parts = path.split('.')
    var value: CialloValue = globalScope.lookup(parts[0])
      ?: environment.symbols[parts[0]]
      ?: return null
    for (index in 1 until parts.size) {
      val obj = value as? CialloValue.Obj ?: return null
      value = obj.fields[parts[index]] ?: return null
    }
    return value
  }

  companion object {
    private val consoleCommands = listOf(":help", ":quit", ":reset", ":env")
    private val commonMethods =
      listOf(
        "add",
        "sub",
        "mul",
        "div",
        "mod",
        "less",
        "lessEq",
        "greater",
        "greaterEq",
        "eq",
        "ne",
        "toInt",
        "toFloat",
        "toMillis",
        "toSeconds",
        "toString",
        "size",
        "length",
        "get",
        "join",
        "apply",
        "run",
        "then",
        "at",
        "config",
        "duration",
        "local",
        "mount",
        "set",
      )
  }
}
