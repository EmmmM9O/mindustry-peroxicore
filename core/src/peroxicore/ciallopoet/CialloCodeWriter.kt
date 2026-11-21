package peroxicore.ciallopoet

import peroxicore.ciallo.value.*

/**
 * Emits [CialloCodeBlock]s, [CialloChainSpec]s, [CialloBlockSpec]s and [CialloFileSpec]s.
 *
 * Ciallo ends a statement at a newline, so unlike KotlinPoet's writer there is no soft line
 * wrapping: every newline in the text is meaningful and emitted as-is. Indentation is emitted
 * lazily, which keeps blank lines free of trailing whitespace.
 */
internal class CialloCodeWriter(
  private val out: Appendable,
  private val indent: String = "  ",
) {
  private var indentLevel = 0

  /** True when the next character would begin a fresh line and still needs its indentation. */
  var isLineStart: Boolean = true
    private set

  /**
   * The line of the statement currently being emitted, or `-1` when no statement is open.
   * Continuation lines of a multi-line statement are indented two levels deeper, matching the
   * layout KotlinPoet uses for wrapped statements.
   */
  private var statementLine = -1

  fun indent(levels: Int = 1): CialloCodeWriter = apply { indentLevel += levels }

  fun unindent(levels: Int = 1): CialloCodeWriter = apply {
    require(indentLevel - levels >= 0) { "cannot unindent $levels from $indentLevel" }
    indentLevel -= levels
  }

  /** Emits [text], honouring embedded newlines and indenting each non-empty line. */
  fun emit(text: String): CialloCodeWriter = apply {
    var first = true
    for (line in text.split('\n')) {
      if (!first) {
        out.append('\n')
        isLineStart = true
        if (statementLine != -1) {
          if (statementLine == 0) indent(2)
          statementLine++
        }
      }
      first = false
      if (line.isEmpty()) continue

      if (isLineStart) {
        repeat(indentLevel) { out.append(indent) }
        isLineStart = false
      }
      out.append(line)
    }
  }

  /** Emits [block], substituting its placeholders. */
  fun emitCode(block: CialloCodeBlock): CialloCodeWriter = apply {
    var argumentIndex = 0
    for (part in block.formatParts) {
      when (part) {
        "%L" -> emitLiteral(block.args[argumentIndex++])
        "%S" -> {
          val value = block.args[argumentIndex++]
          emit(if (value == null) "null" else CialloLiterals.stringLiteral(value.toString()))
        }
        "%N" -> emit(block.args[argumentIndex++] as String)
        "%A" -> emit("@" + (block.args[argumentIndex++] as String))
        "%%" -> emit("%")
        "⇥" -> indent()
        "⇤" -> unindent()
        "«" -> {
          check(statementLine == -1) {
            "cannot open a statement before closing the previous one; " +
              "format parts: ${block.formatParts}"
          }
          statementLine = 0
        }
        "»" -> {
          check(statementLine != -1) {
            "cannot close a statement that was not opened; format parts: ${block.formatParts}"
          }
          if (statementLine > 0) unindent(2)
          statementLine = -1
        }
        "·", "♢" -> emit(" ")
        else -> emit(part)
      }
    }
  }

  /** Emits [chain] as an expression. */
  fun emitChain(chain: CialloChainSpec) = apply { chain.emitInto(this) }

  /** Emits [block] as a `//` comment, prefixing every line. */
  fun emitComment(block: CialloCodeBlock): CialloCodeWriter = apply {
    if (block.isEmpty()) return@apply
    val text = emitNestedString { emitCode(block) }.trimEnd('\n')
    for ((index, line) in text.split('\n').withIndex()) {
      if (index > 0) emit("\n")
      emit(if (line.isEmpty()) "//" else "// " + line)
    }
    emit("\n")
  }

  /** Renders [body] into a fresh writer, so it neither inherits nor disturbs the current state. */
  private fun emitNestedString(body: CialloCodeWriter.() -> Unit): String =
    buildString { CialloCodeWriter(this@buildString).body() }

  private fun emitLiteral(value: Any?) {
    when (value) {
      // A nested fragment is an independent statement sequence: it must not inherit the outer
      // statement's continuation indentation, and it must not leave a statement open.
      is CialloCodeBlock -> emitNested { emitCode(value) }
      is CialloBlockSpec -> emitNested { value.emit(this) }
      is CialloChainSpec -> emitNested { value.emitInto(this) }
      is CialloValue -> emit(CialloLiterals.valueLiteral(value))
      is CharSequence -> emit(value.toString())
      is Char -> emit(value.toString())
      is Number -> emit(CialloLiterals.numberLiteral(value))
      else -> emit(value.toString())
    }
  }

  private fun emitNested(body: () -> Unit) {
    val outerStatement = statementLine
    statementLine = -1
    try {
      body()
    } finally {
      statementLine = outerStatement
    }
  }
}
