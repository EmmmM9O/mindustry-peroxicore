package peroxicore.ui

import arc.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.scene.*
import arc.scene.actions.*
import arc.scene.style.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.Align
import mindustry.*
import mindustry.gen.*
import mindustry.ui.*

/**
 * Builds the text actor displayed by a [PCodeBlock].
 *
 * The actor is expected to lay its content out without wrapping and to report its natural,
 * unbounded size through [Element.getPrefWidth] / [Element.getPrefHeight]; [PCodeBlock] wraps it
 * in a horizontally scrolling [ScrollPane].
 *
 * This is the abstraction seam: the `lwjgl` font module plugs its ligature-aware
 * `peroxicore.font.PLabel` in here instead of arc's bitmap [Label].
 *
 * [tokens] are the highlight ranges produced by [peroxicore.ui.CodeHighlighter]; an empty list
 * means the code is drawn in one uniform color. Splitting highlighted text into runs is the
 * factory's job, since each renderer expresses color differently (arc markup vs. per-draw tint).
 */
interface CodeTextFactory {
  fun create(code: String, tokens: List<CodeToken>, fontScale: Float): Element

  /**
   * Builds the line-number gutter for [lineCount] lines, or `null` for no gutter.
   *
   * Implementations must render one line per entry with exactly the same font and scale as
   * [create], so the numbers line up with the code.
   */
  fun gutter(lineCount: Int, fontScale: Float): Element? = null

  /** Color scheme of this factory, if any; [PCodeBlock] uses it for its background fill. */
  val palette: CodePalette? get() = null
}

/**
 * Default [CodeTextFactory]: an arc [Label] colored through arc's `[#rrggbb]` markup.
 *
 * Arc only interprets markup when `FontData.markupEnabled` is set — Mindustry enables it for
 * `Fonts.def`/`outline` but not for `Fonts.monospace` — so [enableMarkup] turns it on for the font.
 * Source text is escaped (`[` → `[[`) because a literal bracket would otherwise be parsed as a
 * color tag.
 *
 * [font] is resolved to `Fonts.monospace` on first use when left `null`, so constructing the
 * factory does not require loaded game assets.
 */
open class LabelCodeTextFactory @JvmOverloads constructor(
  var font: Font? = null,
  override var palette: CodePalette = CodePalette(),
  var enableMarkup: Boolean = true,
) : CodeTextFactory {
  override fun create(code: String, tokens: List<CodeToken>, fontScale: Float): Element {
    val resolved = font ?: Fonts.monospace.also { font = it }
    if (enableMarkup) resolved.data.markupEnabled = true
    val style = Label.LabelStyle().apply {
      font = resolved
      palette.foreground?.let { fontColor = it }
    }
    return Label(markedUp(code, tokens), style)
      .apply {
        setWrap(false)
        setFontScale(fontScale)
      }
  }

  override fun gutter(lineCount: Int, fontScale: Float): Element {
    val resolved = font ?: Fonts.monospace.also { font = it }
    val style = Label.LabelStyle().apply {
      font = resolved
      palette.comment?.let { fontColor = it }
    }
    return Label(gutterText(lineCount), style)
      .apply {
        setWrap(false)
        setFontScale(fontScale)
        setAlignment(Align.right, Align.right)
      }
  }

  /** The gutter's text: one right-aligned-number per line, joined by newlines. */
  fun gutterText(lineCount: Int): String = (1..lineCount.coerceAtLeast(1)).joinToString("\n")

  /** Renders [code] with arc color markup applied to [tokens]. */
  fun markedUp(code: String, tokens: List<CodeToken>): String {
    if (tokens.isEmpty()) return escapeMarkup(code)

    val out = StringBuilder(code.length + tokens.size * 10)
    var cursor = 0
    for (token in tokens) {
      if (token.start > cursor) out.append(escapeMarkup(code, cursor, token.start))
      val color = palette.colorOf(token.type)
      if (color != null) out.append("[#").append(hex(color)).append(']')
      out.append(escapeMarkup(code, token.start, token.end))
      if (color != null) out.append("[]")
      cursor = token.end
    }
    if (cursor < code.length) out.append(escapeMarkup(code, cursor, code.length))
    return out.toString()
  }

  private fun escapeMarkup(code: String, start: Int = 0, end: Int = code.length): String {
    val out = StringBuilder(end - start)
    for (i in start until end) {
      val ch = code[i]
      if (ch == '[') out.append("[[")
      else out.append(ch)
    }
    return out.toString()
  }

  private fun hex(color: Color): String {
    val rgb = color.rgb888()
    val digits = Integer.toHexString(rgb)
    return "0".repeat(6 - digits.length) + digits
  }
}

/**
 * A standalone code block element modeled after the markdown module's `DrawCodeBlock`:
 * non-wrapping, horizontally scrollable source text with an optional line-number gutter, the
 * language tag and a copy-to-clipboard button in the top-right corner.
 *
 * The text itself comes from [textFactory], so renderers other than arc's bitmap [Label] can be
 * used (see `peroxicore.font.PLabelCodeTextFactory` in the `lwjgl` font module). The block is
 * sized to its content; put it in a cell with `left()`/`top()` to avoid stretching it.
 *
 * @param code raw source text
 * @param textFactory creates the text actor; see [CodeTextFactory]
 * @param tokens highlight ranges to color the source with; empty for no highlighting
 * @param language language tag shown in the header (empty hides it)
 * @param fontScale extra scale applied to the text
 * @param lineNumbers whether to show the gutter built by [CodeTextFactory.gutter]
 * @param sliderStyle scroll pane style of the horizontal scrolling area
 * @param copyStyle style of the copy button
 * @param background optional background drawn behind the block; defaults to the palette's
 *   [CodePalette.background] so the block stands out from the surrounding dialog
 */
open class PCodeBlock @JvmOverloads constructor(
  val code: String,
  val textFactory: CodeTextFactory = LabelCodeTextFactory(),
  val tokens: List<CodeToken> = emptyList(),
  val language: String = "",
  val fontScale: Float = 1f,
  val lineNumbers: Boolean = true,
  val sliderStyle: ScrollPane.ScrollPaneStyle = Styles.noBarPane,
  val copyStyle: TextButton.TextButtonStyle = Styles.nonet,
  background: Drawable? = null,
) : Table(background ?: paletteBackground(textFactory)) {

  /** The text actor created by [textFactory]. */
  val text: Element = textFactory.create(code, tokens, fontScale)

  /** Line-number gutter, or `null` when disabled or unsupported by [textFactory]. */
  val gutter: Element? =
    if (lineNumbers) {
      val lines = code.count { it == '\n' } + 1
      textFactory.gutter(lines, fontScale)
    } else null

  /** Vertical scroll container shared by the gutter and [text]. */
  val pane: ScrollPane = ScrollPane(
    // top().left(): the scroll pane stretches its widget to at least its own size, and a Table
    // centres its content by default, which would push the code into the middle of the block.
    Table().apply {
      top().left()
      if (gutter != null) add(gutter).top().padRight(10f)
      add(text).top().left()
    },
    sliderStyle,
  ).apply {
    isScrollingDisabledY = true
  }

  /** Copy button, exposed so callers can re-theme or reposition it. */
  val copyButton: TextButton

  /** Language tag shown at the top-right, or `null` when [language] is blank. */
  val languageLabel: Label? = language.takeIf { it.isNotBlank() }?.let { tag ->
    Label(tag, Label.LabelStyle().apply {
      font = (textFactory as? LabelCodeTextFactory)?.font ?: Fonts.monospace
      textFactory.palette?.comment?.let { fontColor = it }
    }).apply {
      setFontScale(fontScale * 0.9f)
      color.a = 0.7f
    }
  }

  init {
    val overlay = Table()
    // Header row laid out right-to-left: language tag, then the copy button.
    languageLabel?.let { overlay.add(it).padRight(8f).top() }

    copyButton = overlay.button(Core.bundle["editor.copy"], Icon.copySmall, copyStyle) {
      Core.app.clipboardText = code
      Vars.ui.showInfoFade(Core.bundle["copied"])
    }.get().apply {
      color.a = 0.4f
      hovered { actions(Actions.alpha(1f, 0.3f)) }
      exited { actions(Actions.alpha(0.4f, 0.3f)) }
    }
    overlay.top().right()

    margin(10f)
    add(Stack(pane, overlay)).grow()
  }

  private companion object {
    /** A flat [CodePalette.background] fill, or `null` when the factory has no palette. */
    fun paletteBackground(factory: CodeTextFactory): Drawable? {
      val color = factory.palette?.background ?: return null
      return (Tex.whiteui as TextureRegionDrawable).tint(color)
    }
  }
}
