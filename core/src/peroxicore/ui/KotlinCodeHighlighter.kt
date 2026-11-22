package peroxicore.ui

import org.antlr.v4.kotlinruntime.*
import peroxicore.parsers.*

/**
 * [CodeHighlighter] for Kotlin source, driven by the generated [KotlinLexer].
 *
 * Only the lexer is used, so incomplete snippets still highlight; unknown characters keep the
 * default foreground color. Classification heuristics on top of plain token types:
 *
 * - an identifier after `fun`, or called like a function (`foo(` / `foo {`), is
 *   [CodeTokenType.FUNCTION];
 * - an identifier after `.` / `::` (member access), `:`, `class`/`interface`/`object`/`typealias`,
 *   an annotation `@Foo`, or with an uppercase initial (type reference) is [CodeTokenType.SYMBOL].
 *
 * Color choice is left to the caller through [CodePalette]; this object only emits [CodeToken]s.
 */
object KotlinCodeHighlighter : CodeHighlighter {

  /** One token with whitespace-trimmed bounds. */
  private class LexToken(
    val type: Int,
    val start: Int,
    val end: Int,
    /** Whether this token participates in classification (i.e. is not whitespace/comment). */
    val significant: Boolean,
    /** First visible character, used to spot type-like (uppercase) identifiers. */
    val firstChar: Char,
  )

  override fun highlight(code: String): List<CodeToken> {
    if (code.isEmpty()) return emptyList()
    return try {
      val tokens = lex(code)
      val previous = previousSignificant(tokens)
      val next = nextSignificant(tokens)

      val out = ArrayList<CodeToken>(tokens.size)
      for (i in tokens.indices) {
        val token = tokens[i]
        if (token.type in ignoredTokens) continue

        val prevType = previous[i].takeIf { it >= 0 }?.let { tokens[it].type }
        val nextType = next[i].takeIf { it >= 0 }?.let { tokens[it].type }
        val type = classify(token, prevType, nextType) ?: continue
        out.add(CodeToken(token.start, token.end, type))
      }
      out
    } catch (_: Exception) {
      emptyList()
    }
  }

  // -----------------------------------------------------------------------------------
  // Lexing
  // -----------------------------------------------------------------------------------

  private fun lex(code: String): List<LexToken> {
    val lexer = KotlinLexer(CharStreams.fromString(code, "<highlight>"))
    lexer.removeErrorListeners()
    // Token ranges come from the lexer's own indices, so recovery is harmless here.
    lexer.addErrorListener(SilentErrorListener)

    val tokens = ArrayList<LexToken>()
    var token = lexer.nextToken()
    while (token.type != Token.EOF) {
      val stop = (token.stopIndex + 1).coerceAtMost(code.length)
      var start = token.startIndex.coerceAtLeast(0)
      var end = stop

      // Some operator tokens swallow the whitespace that disambiguates them (`! `, `? `, `@ `,
      // `!is `), so the range is trimmed to the visible characters.
      while (end > start && code[end - 1].isBlankChar()) end--
      while (start < end && code[start].isBlankChar()) start++

      if (end > start) {
        tokens.add(
          LexToken(
            type = token.type,
            start = start,
            end = end,
            significant = token.type !in ignoredTokens,
            firstChar = code[start],
          )
        )
      }
      token = lexer.nextToken()
    }
    return tokens
  }

  /** Maps each index to the previous significant token index, or -1. */
  private fun previousSignificant(tokens: List<LexToken>): IntArray {
    val out = IntArray(tokens.size) { -1 }
    var last = -1
    for (i in tokens.indices) {
      out[i] = last
      if (tokens[i].significant) last = i
    }
    return out
  }

  /** Maps each index to the next significant token index, or -1. */
  private fun nextSignificant(tokens: List<LexToken>): IntArray {
    val out = IntArray(tokens.size) { -1 }
    var last = -1
    for (i in tokens.indices.reversed()) {
      out[i] = last
      if (tokens[i].significant) last = i
    }
    return out
  }

  // -----------------------------------------------------------------------------------
  // Classification
  // -----------------------------------------------------------------------------------

  private fun classify(token: LexToken, prevType: Int?, nextType: Int?): CodeTokenType? =
    when (token.type) {
      in commentTokens -> CodeTokenType.COMMENT
      in stringTokens -> CodeTokenType.STRING
      in numberTokens -> CodeTokenType.NUMBER
      in keywordTokens -> CodeTokenType.KEYWORD
      in operatorTokens -> CodeTokenType.OPERATOR
      KotlinLexer.Tokens.Identifier,
      KotlinLexer.Tokens.IdentifierAt -> classifyIdentifier(token, prevType, nextType)
      else -> null
    }

  private fun classifyIdentifier(
    token: LexToken,
    prevType: Int?,
    nextType: Int?,
  ): CodeTokenType? {
    // Annotation names: `@Foo`, `@get:Foo`.
    if (prevType in annotationPrefixes) return CodeTokenType.SYMBOL

    // `fun foo(...)`.
    if (prevType == KotlinLexer.Tokens.FUN) return CodeTokenType.FUNCTION

    // Called like a function: `foo(...)` or `foo { ... }`.
    if (nextType == KotlinLexer.Tokens.LPAREN || nextType == KotlinLexer.Tokens.LCURL) {
      return CodeTokenType.FUNCTION
    }

    // Member access / reference: `a.b`, `A::b`.
    if (prevType == KotlinLexer.Tokens.DOT || prevType == KotlinLexer.Tokens.COLONCOLON) {
      return CodeTokenType.SYMBOL
    }

    // Type position: `val x: Type`, `class Foo`, `object Bar`, `typealias Baz`.
    if (prevType in typeIntroducers) return CodeTokenType.SYMBOL

    // Type references by naming convention (`String`, `List`).
    if (token.firstChar.isUpperCase()) return CodeTokenType.SYMBOL

    return null
  }

  private fun Char.isBlankChar(): Boolean =
    this == ' ' || this == '\t' || this == '\r' || this == '\n' || this == '\u000C'

  private object SilentErrorListener : BaseErrorListener()

  // -----------------------------------------------------------------------------------
  // Token sets
  // -----------------------------------------------------------------------------------

  /** Tokens kept out of highlighting: whitespace, newlines, shebang and string-fragment markers. */
  private val ignoredTokens =
    setOf(
      KotlinLexer.Tokens.WS,
      KotlinLexer.Tokens.Inside_WS,
      KotlinLexer.Tokens.NL,
      KotlinLexer.Tokens.Inside_NL,
      KotlinLexer.Tokens.ShebangLine,
      KotlinLexer.Tokens.ErrorCharacter,
      KotlinLexer.Tokens.UNICODE_CLASS_LL,
      KotlinLexer.Tokens.UNICODE_CLASS_LM,
      KotlinLexer.Tokens.UNICODE_CLASS_LO,
      KotlinLexer.Tokens.UNICODE_CLASS_LT,
      KotlinLexer.Tokens.UNICODE_CLASS_LU,
      KotlinLexer.Tokens.UNICODE_CLASS_ND,
      KotlinLexer.Tokens.UNICODE_CLASS_NL,
    )

  private val commentTokens =
    setOf(
      KotlinLexer.Tokens.DelimitedComment,
      KotlinLexer.Tokens.LineComment,
      KotlinLexer.Tokens.Inside_Comment,
    )

  /**
   * String tokens. The lexer switches into a string mode, so quotes and the text in between are
   * separate token types; all of them are painted as strings.
   */
  private val stringTokens =
    setOf(
      KotlinLexer.Tokens.QUOTE_OPEN,
      KotlinLexer.Tokens.TRIPLE_QUOTE_OPEN,
      KotlinLexer.Tokens.QUOTE_CLOSE,
      KotlinLexer.Tokens.TRIPLE_QUOTE_CLOSE,
      KotlinLexer.Tokens.MultiLineStringQuote,
      KotlinLexer.Tokens.LineStrText,
      KotlinLexer.Tokens.MultiLineStrText,
      KotlinLexer.Tokens.LineStrEscapedChar,
      KotlinLexer.Tokens.LineStrRef,
      KotlinLexer.Tokens.MultiLineStrRef,
      KotlinLexer.Tokens.LineStrExprStart,
      KotlinLexer.Tokens.MultiLineStrExprStart,
      KotlinLexer.Tokens.CharacterLiteral,
      KotlinLexer.Tokens.FieldIdentifier,
    )

  private val numberTokens =
    setOf(
      KotlinLexer.Tokens.RealLiteral,
      KotlinLexer.Tokens.FloatLiteral,
      KotlinLexer.Tokens.DoubleLiteral,
      KotlinLexer.Tokens.LongLiteral,
      KotlinLexer.Tokens.IntegerLiteral,
      KotlinLexer.Tokens.HexLiteral,
      KotlinLexer.Tokens.BinLiteral,
    )

  private val keywordTokens =
    setOf(
      KotlinLexer.Tokens.PACKAGE,
      KotlinLexer.Tokens.IMPORT,
      KotlinLexer.Tokens.CLASS,
      KotlinLexer.Tokens.INTERFACE,
      KotlinLexer.Tokens.FUN,
      KotlinLexer.Tokens.OBJECT,
      KotlinLexer.Tokens.VAL,
      KotlinLexer.Tokens.VAR,
      KotlinLexer.Tokens.TYPE_ALIAS,
      KotlinLexer.Tokens.CONSTRUCTOR,
      KotlinLexer.Tokens.BY,
      KotlinLexer.Tokens.COMPANION,
      KotlinLexer.Tokens.INIT,
      KotlinLexer.Tokens.THIS,
      KotlinLexer.Tokens.SUPER,
      KotlinLexer.Tokens.TYPEOF,
      KotlinLexer.Tokens.WHERE,
      KotlinLexer.Tokens.IF,
      KotlinLexer.Tokens.ELSE,
      KotlinLexer.Tokens.WHEN,
      KotlinLexer.Tokens.TRY,
      KotlinLexer.Tokens.CATCH,
      KotlinLexer.Tokens.FINALLY,
      KotlinLexer.Tokens.FOR,
      KotlinLexer.Tokens.DO,
      KotlinLexer.Tokens.WHILE,
      KotlinLexer.Tokens.THROW,
      KotlinLexer.Tokens.RETURN,
      KotlinLexer.Tokens.CONTINUE,
      KotlinLexer.Tokens.BREAK,
      KotlinLexer.Tokens.RETURN_AT,
      KotlinLexer.Tokens.CONTINUE_AT,
      KotlinLexer.Tokens.BREAK_AT,
      KotlinLexer.Tokens.THIS_AT,
      KotlinLexer.Tokens.SUPER_AT,
      KotlinLexer.Tokens.AS,
      KotlinLexer.Tokens.AS_SAFE,
      KotlinLexer.Tokens.IS,
      KotlinLexer.Tokens.IN,
      KotlinLexer.Tokens.NOT_IS,
      KotlinLexer.Tokens.NOT_IN,
      KotlinLexer.Tokens.OUT,
      KotlinLexer.Tokens.GETTER,
      KotlinLexer.Tokens.SETTER,
      KotlinLexer.Tokens.DYNAMIC,
      KotlinLexer.Tokens.PUBLIC,
      KotlinLexer.Tokens.PRIVATE,
      KotlinLexer.Tokens.PROTECTED,
      KotlinLexer.Tokens.INTERNAL,
      KotlinLexer.Tokens.ENUM,
      KotlinLexer.Tokens.SEALED,
      KotlinLexer.Tokens.ANNOTATION,
      KotlinLexer.Tokens.DATA,
      KotlinLexer.Tokens.INNER,
      KotlinLexer.Tokens.TAILREC,
      KotlinLexer.Tokens.OPERATOR,
      KotlinLexer.Tokens.INLINE,
      KotlinLexer.Tokens.INFIX,
      KotlinLexer.Tokens.EXTERNAL,
      KotlinLexer.Tokens.SUSPEND,
      KotlinLexer.Tokens.OVERRIDE,
      KotlinLexer.Tokens.ABSTRACT,
      KotlinLexer.Tokens.FINAL,
      KotlinLexer.Tokens.OPEN,
      KotlinLexer.Tokens.CONST,
      KotlinLexer.Tokens.LATEINIT,
      KotlinLexer.Tokens.VARARG,
      KotlinLexer.Tokens.NOINLINE,
      KotlinLexer.Tokens.CROSSINLINE,
      KotlinLexer.Tokens.REIFIED,
      KotlinLexer.Tokens.EXPECT,
      KotlinLexer.Tokens.ACTUAL,
      KotlinLexer.Tokens.BooleanLiteral,
      KotlinLexer.Tokens.NullLiteral,
      KotlinLexer.Tokens.AT_FILE,
      KotlinLexer.Tokens.AT_FIELD,
      KotlinLexer.Tokens.AT_PROPERTY,
      KotlinLexer.Tokens.AT_GET,
      KotlinLexer.Tokens.AT_SET,
      KotlinLexer.Tokens.AT_RECEIVER,
      KotlinLexer.Tokens.AT_PARAM,
      KotlinLexer.Tokens.AT_SETPARAM,
      KotlinLexer.Tokens.AT_DELEGATE,
    )

  private val operatorTokens =
    setOf(
      KotlinLexer.Tokens.RESERVED,
      KotlinLexer.Tokens.DOT,
      KotlinLexer.Tokens.COMMA,
      KotlinLexer.Tokens.LPAREN,
      KotlinLexer.Tokens.RPAREN,
      KotlinLexer.Tokens.LSQUARE,
      KotlinLexer.Tokens.RSQUARE,
      KotlinLexer.Tokens.LCURL,
      KotlinLexer.Tokens.RCURL,
      KotlinLexer.Tokens.MULT,
      KotlinLexer.Tokens.MOD,
      KotlinLexer.Tokens.DIV,
      KotlinLexer.Tokens.ADD,
      KotlinLexer.Tokens.SUB,
      KotlinLexer.Tokens.INCR,
      KotlinLexer.Tokens.DECR,
      KotlinLexer.Tokens.CONJ,
      KotlinLexer.Tokens.DISJ,
      KotlinLexer.Tokens.EXCL_WS,
      KotlinLexer.Tokens.EXCL_NO_WS,
      KotlinLexer.Tokens.COLON,
      KotlinLexer.Tokens.SEMICOLON,
      KotlinLexer.Tokens.ASSIGNMENT,
      KotlinLexer.Tokens.ADD_ASSIGNMENT,
      KotlinLexer.Tokens.SUB_ASSIGNMENT,
      KotlinLexer.Tokens.MULT_ASSIGNMENT,
      KotlinLexer.Tokens.DIV_ASSIGNMENT,
      KotlinLexer.Tokens.MOD_ASSIGNMENT,
      KotlinLexer.Tokens.ARROW,
      KotlinLexer.Tokens.DOUBLE_ARROW,
      KotlinLexer.Tokens.RANGE,
      KotlinLexer.Tokens.COLONCOLON,
      KotlinLexer.Tokens.DOUBLE_SEMICOLON,
      KotlinLexer.Tokens.HASH,
      KotlinLexer.Tokens.AT,
      KotlinLexer.Tokens.AT_WS,
      KotlinLexer.Tokens.QUEST_WS,
      KotlinLexer.Tokens.QUEST_NO_WS,
      KotlinLexer.Tokens.LANGLE,
      KotlinLexer.Tokens.RANGLE,
      KotlinLexer.Tokens.LE,
      KotlinLexer.Tokens.GE,
      KotlinLexer.Tokens.EXCL_EQ,
      KotlinLexer.Tokens.EXCL_EQEQ,
      KotlinLexer.Tokens.EQEQ,
      KotlinLexer.Tokens.EQEQEQ,
      KotlinLexer.Tokens.SINGLE_QUOTE,
    )

  /** Tokens that introduce a type name after them. */
  private val typeIntroducers =
    setOf(
      KotlinLexer.Tokens.COLON,
      KotlinLexer.Tokens.CLASS,
      KotlinLexer.Tokens.INTERFACE,
      KotlinLexer.Tokens.OBJECT,
      KotlinLexer.Tokens.TYPE_ALIAS,
    )

  /** Tokens that directly precede an annotation's name. */
  private val annotationPrefixes =
    setOf(
      KotlinLexer.Tokens.AT,
      KotlinLexer.Tokens.AT_WS,
      KotlinLexer.Tokens.AT_FILE,
      KotlinLexer.Tokens.AT_FIELD,
      KotlinLexer.Tokens.AT_PROPERTY,
      KotlinLexer.Tokens.AT_GET,
      KotlinLexer.Tokens.AT_SET,
      KotlinLexer.Tokens.AT_RECEIVER,
      KotlinLexer.Tokens.AT_PARAM,
      KotlinLexer.Tokens.AT_SETPARAM,
      KotlinLexer.Tokens.AT_DELEGATE,
    )
}
