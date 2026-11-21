package peroxicore.graphics

import arc.Core
import arc.files.Fi
import arc.util.ArcRuntimeException
import arc.util.Log

/**
 * A small GLSL preprocessor: `#include`, conditional compilation, `#define`/`#undef`,
 * `#pragma once` and `#line` bookkeeping.
 *
 * Include resolution order for `#include "x"`:
 * 1. relative to the including file,
 * 2. each entry of [includePaths] (then [POShaders.includePaths]) under `shaders/`,
 * 3. `shaders/x` itself.
 * A leading `/` always means "relative to the `shaders/` root".
 *
 * Directive lines are replaced by empty lines so the shader body keeps its line numbers; an
 * `#include` is bracketed by `#line` directives instead, so errors inside an included file and in
 * the remainder of the including file both report the right file and line. The macro header is
 * emitted up front, before the first `#line`, so it costs no line numbers either.
 */
open class Preprocessor(
  /** Handle `#if*`/`#else`/`#endif`; when false those directives are passed to the driver as-is. */
  val conditionals: Boolean = true,
  /** Maximum nesting depth of `#include`, guarding against pathological trees. */
  val maxIncludeDepth: Int = 50,
  /** Emit `#line` directives around includes and at the start of the file. */
  val lineDirectives: Boolean = true,
  /** Extra directories searched by `#include`, relative to the `shaders/` root. */
  val includePaths: List<String> = emptyList(),
  /** Seed the macro table with [POShaders.macros]. */
  val useGlobalDefines: Boolean = true,
  defines: Map<String, String?> = emptyMap(),
) : ShaderProcessor {

  private val predefined = LinkedHashMap(defines)

  override fun process(source: String, context: ShaderContext): String {
    // Everything known before the first line; emitted as a header so the shader text itself keeps
    // its line numbers.
    val header = LinkedHashMap<String, String?>()
    if (useGlobalDefines) header.putAll(POShaders.macros)
    header.putAll(predefined)
    header.putAll(context.defines)

    val run = Run(LinkedHashMap(header), HashSet())

    val out = Out(lineDirectives)
    for ((key, value) in header) out.write("#define $key ${value ?: ""}")

    // `GL_ES` is normally supplied by the driver. Conditionals are resolved here, so mirror what
    // the driver would have done; it stays out of the header to keep the shader portable.
    if (Core.app != null && Core.app.isMobile) run.defs.putIfAbsent("GL_ES", null)

    out.lineDirective(1, context.file)
    context.file?.let { run.stack += it.absolutePath() }
    expand(source, context.file, run, 0, out)

    // Let a following processor (or the caller) see the final macro table.
    context.defines.clear()
    context.defines.putAll(run.defs)
    return out.sb.toString()
  }

  /** Resolves an `#include` target, or `null` if nothing matched. Override to change the policy. */
  open fun resolveInclude(name: String, from: Fi?): Fi? {
    if (name.startsWith("/")) return POShaders.findFiOrNull(name.trimStart('/'))

    from?.parent()?.child(name)?.takeIf { it.exists() }?.let { return it }

    for (dir in includePaths + POShaders.includePaths) {
      val path = if (dir.isEmpty()) name else "${dir.trimEnd('/')}/$name"
      POShaders.findFiOrNull(path)?.takeIf { it.exists() }?.let { return it }
    }
    return POShaders.findFiOrNull(name)?.takeIf { it.exists() }
  }

  private fun expand(
    text: String,
    file: Fi?,
    run: Run,
    depth: Int,
    out: Out,
  ) {
    if (depth > maxIncludeDepth) fail(file, 0, "Include depth exceeded ($maxIncludeDepth)", run)

    val frames = ArrayDeque<Frame>()
    var lineNo = 0

    for (raw in text.split('\n')) {
      lineNo++
      val line = raw.trimEnd('\r')
      val trimmed = line.trimStart()
      val active = frames.isEmpty() || frames.last().active

      if (!trimmed.startsWith("#")) {
        if (active) out.write(line) else out.write("")
        continue
      }

      val body = trimmed.substring(1).trimStart()
      val name = body.takeWhile { !it.isWhitespace() }
      val rest = body.substring(name.length).trim()

      when (name) {
        "" -> out.write("")

        "define", "undef" -> {
          if (active) {
            if (name == "define") defineInto(run, rest)
            if (name == "undef") run.defs.remove(rest.takeWhile { !it.isWhitespace() })
            out.write(trimmed)
          } else out.write("")
        }

        "include" -> {
          if (!active) {
            out.write("")
            continue
          }
          val target = parseIncludeName(rest) ?: fail(file, lineNo, "Malformed #include: $rest", run)
          val inc = resolveInclude(target, file)
            ?: fail(file, lineNo, "Include not found: $target", run)
          val key = inc.absolutePath()

          if (key in run.once) {
            out.write("")
            continue
          }
          if (key in run.stack) {
            fail(file, lineNo, "Circular include of $key (${(run.stack + key).joinToString(" -> ")})", run)
          }

          run.stack += key
          out.lineDirective(1, inc)
          expand(inc.readString(), inc, run, depth + 1, out)
          run.stack.removeAt(run.stack.lastIndex)
          // Restore the including file's numbering for whatever follows the include.
          out.lineDirective(lineNo + 1, file)
          // Keep the "one input line, one output line" invariant when `#line` is not used.
          if (!lineDirectives) out.write("")
        }

        "pragma" -> {
          if (!active) {
            out.write("")
          } else if (rest.trim() == "once") {
            file?.let { run.once += it.absolutePath() }
            out.write("")
          } else out.write(trimmed)
        }

        "error" -> {
          if (active) fail(file, lineNo, rest.ifEmpty { "#error" }, run)
          out.write("")
        }

        "warning" -> {
          if (active) Log.warn("[shaders] ${file?.path() ?: "<string>"}:$lineNo $rest")
          out.write("")
        }

        "ifdef", "ifndef", "if" -> {
          if (!conditionals) {
            out.write(trimmed)
            continue
          }
          val parentActive = frames.isEmpty() || frames.last().active
          val value =
            if (!parentActive) false
            else when (name) {
              "ifdef" -> run.defs.containsKey(rest.takeWhile { !it.isWhitespace() })
              "ifndef" -> !run.defs.containsKey(rest.takeWhile { !it.isWhitespace() })
              else -> evaluate(rest, run, file, lineNo)
            }
          frames.addLast(Frame(parentActive, parentActive && value, value))
          out.write("")
        }

        "elif" -> {
          if (!conditionals) {
            out.write(trimmed)
            continue
          }
          val frame = frames.lastOrNull() ?: fail(file, lineNo, "#elif without #if", run)
          val value =
            if (frame.parentActive && !frame.taken) evaluate(rest, run, file, lineNo) else false
          frame.active = frame.parentActive && value
          frame.taken = frame.taken || value
          out.write("")
        }

        "else" -> {
          if (!conditionals) {
            out.write(trimmed)
            continue
          }
          val frame = frames.lastOrNull() ?: fail(file, lineNo, "#else without #if", run)
          frame.active = frame.parentActive && !frame.taken
          frame.taken = true
          out.write("")
        }

        "endif" -> {
          if (!conditionals) {
            out.write(trimmed)
            continue
          }
          if (frames.isEmpty()) fail(file, lineNo, "#endif without #if", run)
          frames.removeLast()
          out.write("")
        }

        // #version, #extension and anything else: let the driver see it.
        else -> out.write(if (active) trimmed else "")
      }
    }

    if (frames.isNotEmpty()) fail(file, lineNo, "${frames.size} unterminated #if block(s)", run)
  }

  private fun defineInto(run: Run, rest: String) {
    val name = rest.takeWhile { it.isLetterOrDigit() || it == '_' }
    if (name.isEmpty()) return
    val suffix = rest.substring(name.length)
    // Function-like macro: keep it, but only its existence matters for #ifdef.
    run.defs[name] = if (suffix.startsWith("(")) "" else suffix.trim()
  }

  private fun parseIncludeName(rest: String): String? {
    val text = rest.substringBefore("//").trim()
    if (text.length < 2) return null
    return when {
      text.startsWith("\"") -> text.substringAfter('"').substringBefore('"').takeIf { '"' in text.drop(1) }
      text.startsWith("<") -> text.substringAfter('<').substringBefore('>').takeIf { '>' in text.drop(1) }
      else -> null
    }
  }

  private fun evaluate(rest: String, run: Run, file: Fi?, lineNo: Int): Boolean {
    val expr = rest.substringBefore("//").trim()
    if (expr.isEmpty()) fail(file, lineNo, "Empty #if expression", run)
    return try {
      ConditionExpr(expr, run.defs).evaluate() != 0L
    } catch (e: IllegalArgumentException) {
      fail(file, lineNo, "Bad #if expression `$expr`: ${e.message}", run)
    }
  }

  private fun fail(file: Fi?, lineNo: Int, message: String, run: Run): Nothing {
    val where = "${file?.path() ?: "<inline shader>"}:$lineNo"
    val chain = if (run.stack.size > 1) " (include chain: ${run.stack.joinToString(" -> ")})" else ""
    throw ArcRuntimeException("$message at $where$chain")
  }

  private class Run(val defs: MutableMap<String, String?>, val once: MutableSet<String>) {
    val stack = mutableListOf<String>()
  }

  private class Frame(val parentActive: Boolean, var active: Boolean, var taken: Boolean)

  private class Out(val lines: Boolean) {
    val sb = StringBuilder()

    fun write(text: String) {
      sb.append(text).append('\n')
    }

    fun lineDirective(number: Int, file: Fi?) {
      if (!lines) return
      // The path goes on its own comment line: `#line` only takes integers, and keeping the comment
      // out of the directive avoids relying on comment stripping before directive parsing.
      file?.let { sb.append("// ").append(it.path()).append('\n') }
      sb.append("#line ").append(number).append('\n')
    }
  }
}

/**
 * Integer expression evaluator for `#if`/`#elif`.
 *
 * Supports `defined(X)`/`defined X`, identifiers (expanded through the macro table, undefined = 0),
 * decimal/hex/binary literals, unary `! - ~ +`, and the C operator set including `||`, `&&`,
 * bitwise ops, comparisons, shifts and arithmetic. Floats and strings are not supported, matching
 * the subset of the C preprocessor that makes sense in GLSL.
 */
private class ConditionExpr(
  private val text: String,
  private val defs: Map<String, String?>,
  private val depth: Int = 0,
) {
  private var pos = 0

  fun evaluate(): Long {
    val value = or()
    skipWs()
    require(pos == text.length) { "unexpected '${text.substring(pos)}'" }
    return value
  }

  private fun or(): Long {
    var v = and()
    while (eat("||")) {
      val r = and()
      v = if (v != 0L || r != 0L) 1 else 0
    }
    return v
  }

  private fun and(): Long {
    var v = bitor()
    while (eat("&&")) {
      val r = bitor()
      v = if (v != 0L && r != 0L) 1 else 0
    }
    return v
  }

  private fun bitor(): Long {
    var v = bitxor()
    while (eat("|") ) v = v or bitxor()
    return v
  }

  private fun bitxor(): Long {
    var v = bitand()
    while (eat("^")) v = v xor bitand()
    return v
  }

  private fun bitand(): Long {
    var v = equality()
    while (eat("&")) v = v and equality()
    return v
  }

  private fun equality(): Long {
    var v = relational()
    while (true) {
      v =
        when {
          eat("==") -> if (v == relational()) 1 else 0
          eat("!=") -> if (v != relational()) 1 else 0
          else -> return v
        }
    }
  }

  private fun relational(): Long {
    var v = shift()
    while (true) {
      v =
        when {
          eat("<=") -> if (v <= shift()) 1 else 0
          eat(">=") -> if (v >= shift()) 1 else 0
          eat("<") -> if (v < shift()) 1 else 0
          eat(">") -> if (v > shift()) 1 else 0
          else -> return v
        }
    }
  }

  private fun shift(): Long {
    var v = additive()
    while (true) {
      v =
        when {
          eat("<<") -> v shl additive().toInt()
          eat(">>") -> v shr additive().toInt()
          else -> return v
        }
    }
  }

  private fun additive(): Long {
    var v = multiplicative()
    while (true) {
      v =
        when {
          eat("+") -> v + multiplicative()
          eat("-") -> v - multiplicative()
          else -> return v
        }
    }
  }

  private fun multiplicative(): Long {
    var v = unary()
    while (true) {
      v =
        when {
          eat("*") -> v * unary()
          eat("/") -> {
            val d = unary()
            if (d == 0L) 0L else v / d
          }
          eat("%") -> {
            val d = unary()
            if (d == 0L) 0L else v % d
          }
          else -> return v
        }
    }
  }

  private fun unary(): Long {
    skipWs()
    return when {
      eat("!") -> if (unary() == 0L) 1 else 0
      eat("~") -> unary().inv()
      eat("-") -> -unary()
      eat("+") -> unary()
      else -> primary()
    }
  }

  private fun primary(): Long {
    skipWs()
    require(pos < text.length) { "unexpected end of expression" }

    if (text[pos] == '(') {
      pos++
      val v = or()
      skipWs()
      require(pos < text.length && text[pos] == ')') { "missing ')'" }
      pos++
      return v
    }

    if (text[pos].isDigit()) return parseNumber(readNumber()) ?: 0L

    val name = readIdentifier()
    require(name.isNotEmpty()) { "unexpected '${text.substring(pos)}'" }

    if (name == "defined") {
      skipWs()
      val paren = pos < text.length && text[pos] == '('
      if (paren) {
        pos++
        skipWs()
      }
      val macro = readIdentifier()
      require(macro.isNotEmpty()) { "defined() needs a name" }
      if (paren) eat(")")
      return if (defs.containsKey(macro)) 1 else 0
    }

    return resolve(name, depth)
  }

  /**
   * Expands [name] through the macro table.
   *
   * Follows C: a macro with no body counts as defined-but-empty and evaluates to 1 (so
   * `#define DEBUG` + `#if DEBUG` works, which plain C rejects); a numeric body is its value; any
   * other body is itself evaluated as an expression, so undefined identifiers become 0.
   */
  private fun resolve(name: String, depth: Int): Long {
    if (depth > 16) return 0
    val raw = defs[name] ?: return 0
    val value = raw.trim()
    if (value.isEmpty()) return 1
    parseNumber(value)?.let { return it }
    return try {
      ConditionExpr(value, defs, depth + 1).evaluate()
    } catch (_: IllegalArgumentException) {
      0
    }
  }

  private fun parseNumber(raw: String): Long? {
    val s = raw.trim().trimEnd('u', 'U', 'l', 'L')
    if (s.isEmpty()) return null
    return try {
      when {
        s.startsWith("0x", true) -> s.substring(2).toLong(16)
        s.startsWith("0b", true) -> s.substring(2).toLong(2)
        s.any { it == '.' || it == 'e' || it == 'E' } -> s.toDouble().toLong()
        else -> s.toLong()
      }
    } catch (_: NumberFormatException) {
      null
    }
  }

  private fun readNumber(): String {
    val start = pos
    while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '.')) pos++
    return text.substring(start, pos)
  }

  private fun readIdentifier(): String {
    val start = pos
    while (pos < text.length && (text[pos] == '_' || text[pos].isLetterOrDigit())) pos++
    return text.substring(start, pos)
  }

  private fun eat(op: String): Boolean {
    skipWs()
    if (!text.startsWith(op, pos)) return false
    // `|` must not swallow `||`, `<` must not swallow `<<`, etc.
    if (op.length == 1 && op[0] in "|&<>" && pos + 1 < text.length) {
      val next = text[pos + 1]
      if ((op == "|" && next == '|') ||
        (op == "&" && next == '&') ||
        (op == "<" && (next == '<' || next == '=')) ||
        (op == ">" && (next == '>' || next == '='))
      ) return false
    }
    pos += op.length
    return true
  }

  private fun skipWs() {
    while (pos < text.length && text[pos].isWhitespace()) pos++
  }
}
