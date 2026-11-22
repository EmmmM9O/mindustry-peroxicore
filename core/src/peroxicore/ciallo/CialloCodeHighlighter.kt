package peroxicore.ciallo

import org.antlr.v4.kotlinruntime.*
import peroxicore.parsers.*
import peroxicore.ui.*

/**
 * [CodeHighlighter] for Ciallo source, driven by the generated [CialloLexer].
 *
 * Only the lexer is used, so partial or syntactically invalid snippets still highlight: the token
 * stream is classified as-is and unknown characters simply keep the default color.
 *
 * Identifiers in call position are reported as [CodeTokenType.FUNCTION]: `name(...)` as well as
 * the paren-less form `name value` (`setBlock @Blocks.air`), since the grammar treats both as a
 * call. Identifiers inside an `@symbol.path` are [CodeTokenType.SYMBOL], matching the console
 * highlighter in [CialloHighlighter].
 *
 * Color choice is left to the caller through [CodePalette]; this object only emits
 * [CodeToken]s.
 */
object CialloCodeHighlighter : CodeHighlighter {
  /** Values that the interpreter treats as variable-definition keywords. */
  private val keywordNames: Set<String> = CialloScope.reserved

  override fun highlight(code: String): List<CodeToken> {
    if (code.isEmpty()) return emptyList()
    return try {
      val tokens = lex(code)
      val out = ArrayList<CodeToken>(tokens.size)

      var previousStop = -2
      var inSymbol = false
      for (i in tokens.indices) {
        val token = tokens[i]
        val adjacent = token.start == previousStop + 1
        val at = token.type == CialloLexer.Tokens.AT
        val symbol =
          inSymbol &&
            (token.type == CialloLexer.Tokens.DOT ||
              (token.type == CialloLexer.Tokens.IDENTIFIER && adjacent))

        val type = classify(token.type, token.text, at || symbol, isCallHead(tokens, i))
        if (type != null) out.add(CodeToken(token.start, token.end, type))

        inSymbol = at || symbol
        previousStop = token.stop
      }

      out
    } catch (_: Exception) {
      emptyList()
    }
  }

  private class LexToken(val type: Int, val text: String, val start: Int, val end: Int, val stop: Int)

  private fun lex(code: String): List<LexToken> {
    val lexer = CialloLexer(CharStreams.fromString(code, "<highlight>"))
    lexer.removeErrorListeners()
    // Token ranges come from the lexer's own indices, so a recovery error is harmless.
    lexer.addErrorListener(SilentErrorListener)

    val tokens = ArrayList<LexToken>()
    var token = lexer.nextToken()
    while (token.type != Token.EOF) {
      val start = token.startIndex.coerceAtLeast(0)
      val end = (token.stopIndex + 1).coerceAtMost(code.length)
      if (end > start && start < code.length) {
        tokens.add(LexToken(token.type, token.text ?: "", start, end, token.stopIndex))
      }
      token = lexer.nextToken()
    }
    return tokens
  }

  /**
   * Whether the identifier at [index] sits in call position.
   *
   * In the grammar a call segment is `namePath args? block?`, and a bare value (e.g. `@Blocks.air`)
   * is a *separate* segment, so `setBlock @Blocks.air` parses as `setBlock(@Blocks.air)` and
   * `at(2, 5) build @Blocks.duo` parses as `at(2, 5).build(@Blocks.duo)`. Hence any identifier
   * followed by `(`, `{` or a value-starting token is a call.
   */
  private fun isCallHead(tokens: List<LexToken>, index: Int): Boolean =
    tokens.getOrNull(index + 1)?.type.startsValue() == true

  private fun classify(
    tokenType: Int,
    text: String,
    symbol: Boolean,
    callHead: Boolean,
  ): CodeTokenType? =
    when (tokenType) {
      CialloLexer.Tokens.LINE_COMMENT,
      CialloLexer.Tokens.BLOCK_COMMENT -> CodeTokenType.COMMENT

      CialloLexer.Tokens.STRING,
      CialloLexer.Tokens.LONG_STRING -> CodeTokenType.STRING

      CialloLexer.Tokens.INT,
      CialloLexer.Tokens.FLOAT,
      CialloLexer.Tokens.NUM_SUFFIX -> CodeTokenType.NUMBER

      CialloLexer.Tokens.NULL,
      CialloLexer.Tokens.TRUE,
      CialloLexer.Tokens.FALSE -> CodeTokenType.KEYWORD

      CialloLexer.Tokens.IDENTIFIER ->
        when {
          symbol -> CodeTokenType.SYMBOL
          text in keywordNames -> CodeTokenType.KEYWORD
          callHead -> CodeTokenType.FUNCTION
          else -> null
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
      CialloLexer.Tokens.BANG -> CodeTokenType.OPERATOR

      else -> null
    }

  /** Tokens that can start the single-value argument of `name value`. */
  private fun Int?.startsValue(): Boolean =
    this == CialloLexer.Tokens.AT ||
      this == CialloLexer.Tokens.LPAREN ||
      this == CialloLexer.Tokens.LBRACKET ||
      this == CialloLexer.Tokens.LBRACE ||
      this == CialloLexer.Tokens.STRING ||
      this == CialloLexer.Tokens.LONG_STRING ||
      this == CialloLexer.Tokens.INT ||
      this == CialloLexer.Tokens.FLOAT ||
      this == CialloLexer.Tokens.NUM_SUFFIX ||
      this == CialloLexer.Tokens.NULL ||
      this == CialloLexer.Tokens.TRUE ||
      this == CialloLexer.Tokens.FALSE ||
      this == CialloLexer.Tokens.IDENTIFIER

  private object SilentErrorListener : BaseErrorListener()
}
