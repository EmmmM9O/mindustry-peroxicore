package peroxicore.ui

import arc.graphics.*

/** Semantic category of a highlighted code token; colors are supplied by [CodePalette]. */
enum class CodeTokenType {
  COMMENT,
  STRING,
  NUMBER,
  KEYWORD,
  FUNCTION,
  SYMBOL,
  OPERATOR,
}

/**
 * A half-open `[start, end)` character range of a code block carrying a [CodeTokenType].
 *
 * Ranges are produced by a [CodeHighlighter] and are ordered, non-overlapping and within the
 * source text.
 */
data class CodeToken(
  val start: Int,
  val end: Int,
  val type: CodeTokenType,
)

/**
 * Splits source code into highlighted ranges.
 *
 * Implementations are rendering-agnostic: they must not choose colors, so the same result can be
 * drawn by arc's markup-based [arc.scene.ui.Label] and by the ligature-aware `PLabel` in the
 * `lwjgl` font module.
 */
fun interface CodeHighlighter {
  fun highlight(code: String): List<CodeToken>
}

/**
 * A code block color scheme: per-token colors plus the block's own [background] and the default
 * [foreground] used for unclassified text.
 *
 * The default values are the Tokyo Night palette; use [moonlight] for the Moonlight scheme. A
 * `null` token color keeps the surrounding text color.
 */
open class CodePalette @JvmOverloads constructor(
  var comment: Color? = Color.valueOf("51597dff"),
  var string: Color? = Color.valueOf("9ece6aff"),
  var number: Color? = Color.valueOf("ff9e64ff"),
  var keyword: Color? = Color.valueOf("bb9af7ff"),
  var function: Color? = Color.valueOf("7aa2f7ff"),
  var symbol: Color? = Color.valueOf("7dcfffff"),
  var operator: Color? = Color.valueOf("89ddffff"),
  /** Block background; drawn by [PCodeBlock] when it is given no explicit background. */
  var background: Color? = Color.valueOf("1a1b26ff"),
  /** Default text color for unhighlighted source. */
  var foreground: Color? = Color.valueOf("a9b1d6ff"),
) {
  open fun colorOf(type: CodeTokenType): Color? = when (type) {
    CodeTokenType.COMMENT -> comment
    CodeTokenType.STRING -> string
    CodeTokenType.NUMBER -> number
    CodeTokenType.KEYWORD -> keyword
    CodeTokenType.FUNCTION -> function
    CodeTokenType.SYMBOL -> symbol
    CodeTokenType.OPERATOR -> operator
  }

  companion object {
    /** Tokyo Night (default). */
    fun tokyoNight(): CodePalette = CodePalette()

    /** Moonlight. */
    fun moonlight(): CodePalette = CodePalette(
      comment = Color.valueOf("7a88cfff"),
      string = Color.valueOf("7af8caff"),
      number = Color.valueOf("ff966cff"),
      keyword = Color.valueOf("af9fffff"),
      function = Color.valueOf("7cafffff"),
      symbol = Color.valueOf("f8b576ff"),
      operator = Color.valueOf("78dbffff"),
      background = Color.valueOf("222436ff"),
      foreground = Color.valueOf("c8d3f5ff"),
    )
  }
}

/**
 * Picks the [CodeHighlighter] for a fenced code block's info string (its language tag).
 *
 * Returning `null` leaves the block unhighlighted. See [CodeHighlighterResolver.languages] for the
 * built-in language table.
 */
fun interface CodeHighlighterResolver {
  fun resolve(tag: String): CodeHighlighter?

  companion object {
    /** Tags that map to [peroxicore.ciallo.CialloCodeHighlighter]. */
    val CIALLO_TAGS: Set<String> = setOf("", "ciallo")

    /** Tags that map to [KotlinCodeHighlighter]. */
    val KOTLIN_TAGS: Set<String> = setOf("kotlin", "kt", "kts")

    /** Ciallo + Kotlin, matching an empty tag as Ciallo. */
    val DEFAULT: CodeHighlighterResolver = CodeHighlighterResolver { tag ->
      when (tag.trim().lowercase()) {
        in CIALLO_TAGS -> peroxicore.ciallo.CialloCodeHighlighter
        in KOTLIN_TAGS -> KotlinCodeHighlighter
        else -> null
      }
    }
  }
}
