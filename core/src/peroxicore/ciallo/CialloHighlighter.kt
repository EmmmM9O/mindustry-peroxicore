package peroxicore.ciallo

import org.antlr.v4.kotlinruntime.*
import org.jline.reader.*
import org.jline.utils.*
import peroxicore.parsers.*

/** Syntax highlighter for the interactive Ciallo console. */
internal class CialloHighlighter(
  private val environment: CialloEnvironment,
) : Highlighter {
  override fun highlight(reader: LineReader, buffer: String): AttributedString = highlightSource(buffer)

  fun highlightSource(source: String): AttributedString {
    return try {
      val builder = AttributedStringBuilder()
      val lexer = CialloLexer(CharStreams.fromString(source, "<console>"))
      lexer.removeErrorListeners()
      lexer.addErrorListener(SilentErrorListener)

      var cursor = 0
      var previousStop = -2
      var symbol = false
      var token = lexer.nextToken()

      while (token.type != Token.EOF) {
        val start = token.startIndex.coerceAtLeast(0)
        if (start > cursor) {
          builder.append(source.substring(cursor, start))
          cursor = start
        }

        val text = token.text ?: ""
        val adjacent = token.startIndex == previousStop + 1
        val inSymbol =
          symbol &&
            (token.type == CialloLexer.Tokens.DOT ||
              (token.type == CialloLexer.Tokens.IDENTIFIER && adjacent))
        val at = token.type == CialloLexer.Tokens.AT
        builder.styled(styleFor(token.type, text, at || inSymbol), text)
        if (token.stopIndex >= 0) cursor = token.stopIndex + 1
        symbol = at || inSymbol
        previousStop = token.stopIndex
        token = lexer.nextToken()
      }

      if (cursor < source.length) {
        builder.append(source.substring(cursor))
      }

      builder.toAttributedString()
    } catch (_: Exception) {
      AttributedString(source)
    }
  }

  private fun styleFor(type: Int, text: String, symbol: Boolean): AttributedStyle =
    when (type) {
      CialloLexer.Tokens.LINE_COMMENT, CialloLexer.Tokens.BLOCK_COMMENT -> comments
      CialloLexer.Tokens.STRING, CialloLexer.Tokens.LONG_STRING -> strings
      CialloLexer.Tokens.INT, CialloLexer.Tokens.NUM_SUFFIX -> numbers
      CialloLexer.Tokens.NULL, CialloLexer.Tokens.TRUE, CialloLexer.Tokens.FALSE -> keywords
      CialloLexer.Tokens.IDENTIFIER ->
        when {
          symbol -> symbols
          text in environment.natives -> functions
          text in keywordNames -> keywords
          else -> AttributedStyle.DEFAULT
        }
      CialloLexer.Tokens.PLUS,
      CialloLexer.Tokens.MINUS,
      CialloLexer.Tokens.STAR,
      CialloLexer.Tokens.SLASH,
      CialloLexer.Tokens.PERCENT,
      CialloLexer.Tokens.EQ,
      CialloLexer.Tokens.NEQ,
      CialloLexer.Tokens.LT,
      CialloLexer.Tokens.LE,
      CialloLexer.Tokens.GT,
      CialloLexer.Tokens.GE,
      CialloLexer.Tokens.BANG -> operators
      else -> AttributedStyle.DEFAULT
    }

  private object SilentErrorListener : BaseErrorListener()

  private companion object {
    private val keywordNames = setOf("local", "mount", "set", "null", "true", "false")

    private val comments = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).faint()
    private val strings = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
    private val numbers = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
    private val keywords = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold()
    private val functions = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold()
    private val symbols = AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA)
    private val operators = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)
  }
}
