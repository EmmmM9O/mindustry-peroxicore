package peroxicore.ciallo

import org.antlr.v4.kotlinruntime.*
import peroxicore.parsers.*

/**
 * Pretty-printer for Ciallo source.
 *
 * Canonical style:
 * - one statement per line;
 * - two-space indentation (configurable through [format]);
 * - a space before a trailing block, no space before call parentheses;
 * - one space after commas and around binary operators;
 * - nested blocks get one more indentation level.
 *
 * Comments are kept (the grammar puts them on the hidden token channel), and
 * the formatter is idempotent.
 */
object CialloFormatter {
  fun format(source: String, indentUnit: String = "  "): String {
    validate(source)
    val tokens = lex(source)
    val writer = FormatWriter(indentUnit)

    var parenDepth = 0
    var bracketDepth = 0
    var pendingNewlines = 0
    var previous: Token? = null
    var index = 0

    while (index < tokens.size) {
      val token = tokens[index]

      when (token.type) {
        CialloLexer.Tokens.NEWLINE -> {
          if (parenDepth == 0 && bracketDepth == 0) {
            pendingNewlines = minOf(2, pendingNewlines + countNewlines(token.text))
          }
          index++
          continue
        }

        CialloLexer.Tokens.LINE_COMMENT -> {
          flushNewlines(writer, pendingNewlines)
          pendingNewlines = 0
          writer.write(token.text ?: "", spaceBefore = previous != null)
          writer.newline()
          previous = token
          index++
          continue
        }

        CialloLexer.Tokens.BLOCK_COMMENT -> {
          flushNewlines(writer, pendingNewlines)
          pendingNewlines = 0
          val text = token.text ?: ""
          if (text.contains('\n') || text.contains('\r')) {
            writer.newline()
            writeBlockComment(writer, text)
            writer.newline()
          } else {
            writer.write(text, spaceBefore = previous != null)
          }
          previous = token
          index++
          continue
        }

        CialloLexer.Tokens.LBRACE -> {
          flushNewlines(writer, pendingNewlines)
          pendingNewlines = 0
          val close = emptyBlockClose(tokens, index)
          writer.write("{", spaceBefore = previous != null)
          if (close >= 0) {
            writer.write("}")
            previous = tokens[close]
            index = close + 1
          } else {
            writer.newline()
            writer.indentMore()
            previous = token
            index++
          }
          continue
        }

        CialloLexer.Tokens.RBRACE -> {
          pendingNewlines = 0
          writer.newline()
          writer.indentLess()
          writer.write("}")
          writer.newline()
          previous = token
          index++
          continue
        }
      }

      flushNewlines(writer, pendingNewlines)
      pendingNewlines = 0

      when (token.type) {
        CialloLexer.Tokens.LPAREN -> {
          writer.write("(", spaceBefore = needsSpace(previous, token))
          parenDepth++
        }

        CialloLexer.Tokens.RPAREN -> {
          writer.write(")")
          parenDepth--
        }

        CialloLexer.Tokens.LBRACKET -> {
          writer.write("[", spaceBefore = needsSpace(previous, token))
          bracketDepth++
        }

        CialloLexer.Tokens.RBRACKET -> {
          writer.write("]")
          bracketDepth--
        }

        CialloLexer.Tokens.COMMA -> writer.write(",")
        CialloLexer.Tokens.DOT -> writer.write(".")
        CialloLexer.Tokens.AT -> writer.write("@", spaceBefore = needsSpace(previous, token))
        else -> writer.write(token.text ?: "", spaceBefore = needsSpace(previous, token))
      }

      previous = token
      index++
    }

    return writer.finish()
  }

  private fun needsSpace(previous: Token?, current: Token): Boolean {
    if (previous == null) return false
    val previousType = previous.type
    val currentType = current.type

    if (previousType == CialloLexer.Tokens.AT) return false
    if (previousType == CialloLexer.Tokens.DOT) return false
    if (previousType == CialloLexer.Tokens.LPAREN) return false
    if (previousType == CialloLexer.Tokens.LBRACKET) return false
    if (previousType == CialloLexer.Tokens.BANG) return false

    if (currentType == CialloLexer.Tokens.DOT) return false
    if (currentType == CialloLexer.Tokens.LPAREN) {
      return isBinaryOperator(previousType) || previousType == CialloLexer.Tokens.COMMA
    }
    if (currentType == CialloLexer.Tokens.RPAREN) return false
    if (currentType == CialloLexer.Tokens.RBRACKET) return false
    if (currentType == CialloLexer.Tokens.COMMA) return false

    return true
  }

  private fun isBinaryOperator(type: Int): Boolean =
    type == CialloLexer.Tokens.PLUS ||
      type == CialloLexer.Tokens.MINUS ||
      type == CialloLexer.Tokens.STAR ||
      type == CialloLexer.Tokens.SLASH ||
      type == CialloLexer.Tokens.PERCENT ||
      type == CialloLexer.Tokens.EQ ||
      type == CialloLexer.Tokens.NEQ ||
      type == CialloLexer.Tokens.LT ||
      type == CialloLexer.Tokens.LE ||
      type == CialloLexer.Tokens.GT ||
      type == CialloLexer.Tokens.GE

  private fun countNewlines(text: String?): Int {
    if (text == null) return 1
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    return maxOf(1, normalized.count { it == '\n' })
  }

  private fun emptyBlockClose(tokens: List<Token>, openIndex: Int): Int {
    var index = openIndex + 1
    while (index < tokens.size) {
      val type = tokens[index].type
      if (type == CialloLexer.Tokens.NEWLINE) {
        index++
        continue
      }
      if (type == CialloLexer.Tokens.LINE_COMMENT || type == CialloLexer.Tokens.BLOCK_COMMENT) {
        return -1
      }
      return if (type == CialloLexer.Tokens.RBRACE) index else -1
    }
    return -1
  }

  private fun writeBlockComment(writer: FormatWriter, text: String) {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val minIndent =
      lines.drop(1)
        .filter { it.isNotBlank() }
        .map { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
        .minOrNull() ?: 0

    for ((lineIndex, line) in lines.withIndex()) {
      val content =
        if (lineIndex == 0) line.trimEnd()
        else line.drop(minIndent).trimEnd()

      if (content.isNotEmpty()) {
        writer.write(content)
      } else {
        writer.newline()
      }

      if (lineIndex != lines.lastIndex) {
        writer.newline()
      }
    }
  }

  private fun flushNewlines(writer: FormatWriter, count: Int) {
    if (count > 0) writer.newline(blank = count > 1)
  }

  private fun validate(source: String) {
    val lexer = CialloLexer(CharStreams.fromString(source, "<format>"))
    lexer.removeErrorListeners()
    lexer.addErrorListener(CialloErrorListener)

    val parser = CialloParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    parser.addErrorListener(CialloErrorListener)
    parser.program()
  }

  private fun lex(source: String): List<Token> {
    val lexer = CialloLexer(CharStreams.fromString(source, "<format>"))
    lexer.removeErrorListeners()
    lexer.addErrorListener(CialloErrorListener)

    val tokens = mutableListOf<Token>()
    var token = lexer.nextToken()
    while (token.type != Token.EOF) {
      tokens += token
      token = lexer.nextToken()
    }
    return tokens
  }
}

private class FormatWriter(private val indentUnit: String) {
  private val builder = StringBuilder()
  private var indent = 0
  private var atLineStart = true
  private var trailingNewlines = 0

  fun indentMore() {
    indent++
  }

  fun indentLess() {
    if (indent > 0) indent--
  }

  fun newline(blank: Boolean = false) {
    val target = if (blank) 2 else 1
    while (trailingNewlines < target) {
      builder.append('\n')
      trailingNewlines++
    }
    atLineStart = true
  }

  fun write(text: String, spaceBefore: Boolean = false) {
    if (text.isEmpty()) return

    if (atLineStart) {
      repeat(indent) { builder.append(indentUnit) }
      atLineStart = false
      trailingNewlines = 0
    } else if (spaceBefore) {
      builder.append(' ')
    }

    builder.append(text)
    refreshLineState()
  }

  private fun refreshLineState() {
    var count = 0
    var index = builder.length - 1
    while (index >= 0 && builder[index] == '\n') {
      count++
      index--
    }
    trailingNewlines = count
    atLineStart = count > 0
  }

  fun finish(): String {
    val result = builder.toString().trimEnd().trimStart('\n')
    return if (result.isEmpty()) "" else result + "\n"
  }
}
