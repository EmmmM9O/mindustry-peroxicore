package peroxicore.font

import arc.graphics.*
import arc.graphics.g2d.*
import arc.scene.*
import arc.scene.style.*
import arc.util.*
import arc.util.Align
import java.lang.StringBuilder

/**
 * A scene label that renders text with full OpenType shaping (ligatures, kerning, complex scripts)
 * via [[PFreeTypeFont]] which is backed by LWJGL FreeType + HarfBuzz. This is a peroxicore
 * counterpart of `arc.scene.ui.Label` that does not use arc's freetype system.
 *
 * The label's preferred size is the text bounds, unless [[setWrap]] is enabled (in which case the
 * preferred width becomes 0 and the text wraps to the actor's width).
 *
 * Text can also be supplied as colored [Segment]s (see [setSegments]); each segment is shaped on
 * its own, so ligatures are preserved within a segment while segment colors may change mid-line.
 * Colored segments are drawn without wrapping.
 */
class PLabel(font: PFreeTypeFont, text: CharSequence = "") : Element() {

  /** A run of text drawn in one [color]; `null` means "use the label's color". */
  class Segment(val text: CharSequence, val color: Color? = null)

  /** The backing font/shaping engine. */
  var font: PFreeTypeFont = font
    set(value) {
      field = value
      invalidate()
    }

  /** Optional color applied on top of the element [color] when drawing the text. */
  var fontColor: Color? = null

  /** Optional background drawable drawn behind the text. */
  var background: Drawable? = null

  var wrap: Boolean = false
    set(value) {
      field = value
      invalidate()
    }

  /** Colored text runs; empty when the label draws [getText] in one color. */
  var segments: List<Segment> = emptyList()
    private set

  private var labelAlign = Align.left
  private var lineAlign = Align.left

  var fontScaleX: Float = 1f
    set(value) {
      field = value
      invalidate()
    }

  var fontScaleY: Float = 1f
    set(value) {
      field = value
      invalidate()
    }

  private val tempTextColor = Color()
  private val tempPieceColor = Color()
  private val text = StringBuilder(text)
  private var lines: List<Line> = emptyList()
  private var prefSizeInvalid = true

  private class Piece(
    val glyphs: List<PFreeTypeFont.ShapedGlyph>,
    val width: Float,
    val color: Color?,
  )

  private class Line(val pieces: List<Piece>) {
    val width: Float = pieces.sumOf { it.width.toDouble() }.toFloat()
  }

  /** A single line in the middle of a segment split, used while building [lines]. */
  private class OpenLine(val pieces: ArrayList<Piece> = ArrayList())

  init {
    if (text.isNotEmpty()) setSize(getPrefWidth(), getPrefHeight())
  }

  // -----------------------------------------------------------------------------------
  // Public API (mirrors arc.scene.ui.Label)
  // -----------------------------------------------------------------------------------

  fun setText(newText: CharSequence?) {
    val value = newText ?: ""
    if (segments.isEmpty() && textEquals(value)) return
    text.setLength(0)
    text.append(value)
    segments = emptyList()
    invalidate()
  }

  /** Replaces the content with [segments], keeping a plain-text copy for [getText]. */
  fun setSegments(newSegments: List<Segment>) {
    text.setLength(0)
    newSegments.forEach { text.append(it.text) }
    segments = newSegments
    invalidate()
  }

  fun getText(): CharSequence = text

  fun textEquals(other: CharSequence): Boolean {
    if (text.length != other.length) return false
    for (i in text.indices) if (text[i] != other[i]) return false
    return true
  }

  fun setAlignment(alignment: Int) = setAlignment(alignment, alignment)

  fun setAlignment(labelAlign: Int, lineAlign: Int) {
    this.labelAlign = labelAlign
    this.lineAlign =
      when {
        (lineAlign and Align.left) != 0 -> Align.left
        (lineAlign and Align.right) != 0 -> Align.right
        else -> Align.center
      }
    invalidate()
  }

  fun getLabelAlign(): Int = labelAlign

  fun getLineAlign(): Int = lineAlign

  fun setFontScale(scale: Float) = setFontScale(scale, scale)

  fun setFontScale(fontScaleX: Float, fontScaleY: Float) {
    this.fontScaleX = fontScaleX
    this.fontScaleY = fontScaleY
    invalidate()
  }

  override fun invalidate() {
    super.invalidate()
    prefSizeInvalid = true
  }

  // -----------------------------------------------------------------------------------
  // Layout / measurement
  // -----------------------------------------------------------------------------------

  override fun getPrefWidth(): Float {
    if (wrap) return 0f
    val l = measure()
    val w = l.maxOfOrNull { it.width } ?: 0f
    return w + bgLeft() + bgRight()
  }

  override fun getPrefHeight(): Float {
    val l = measure()
    val h = l.size * effLineHeight() + (if (l.isEmpty()) effAscent() - effDescent() else 0f)
    return h + bgBottom() + bgTop()
  }

  override fun layout() {
    lines = buildLines()
  }

  /** Recomputes (and caches) the current lines when the cached size is stale. */
  private fun measure(): List<Line> {
    if (prefSizeInvalid) {
      lines = buildLines()
      prefSizeInvalid = false
    }
    return lines
  }

  private fun buildLines(): List<Line> {
    if (segments.isNotEmpty()) return buildSegmentLines()
    if (text.isEmpty()) return emptyList()
    val maxW = if (wrap) (getWidth() - bgLeft() - bgRight()).coerceAtLeast(0f) else Float.MAX_VALUE
    val out = ArrayList<Line>()
    for (para in text.toString().split('\n')) {
      if (para.isEmpty()) {
        out.add(Line(emptyList()))
        continue
      }
      if (!wrap) {
        val g = font.shape(para)
        out.add(Line(listOf(Piece(g, font.width(g) * fontScaleX, null))))
      } else {
        for (wrapped in wrapParagraph(para, maxW)) {
          out.add(Line(listOf(Piece(wrapped, font.width(wrapped) * fontScaleX, null))))
        }
      }
    }
    return out
  }

  /** Builds one [Line] per newline, with each segment shaped and colored independently. */
  private fun buildSegmentLines(): List<Line> {
    val out = ArrayList<Line>()
    var current = OpenLine()

    for (segment in segments) {
      val value = segment.text.toString()
      var from = 0
      while (true) {
        val newline = value.indexOf('\n', from)
        val chunk = if (newline < 0) value.substring(from) else value.substring(from, newline)
        if (chunk.isNotEmpty()) {
          val glyphs = font.shape(chunk)
          current.pieces.add(Piece(glyphs, font.width(glyphs) * fontScaleX, segment.color))
        }
        if (newline < 0) break
        out.add(Line(current.pieces))
        current = OpenLine()
        from = newline + 1
      }
    }
    out.add(Line(current.pieces))
    return out
  }

  private fun wrapParagraph(para: String, maxW: Float): List<List<PFreeTypeFont.ShapedGlyph>> {
    val out = ArrayList<List<PFreeTypeFont.ShapedGlyph>>()
    var lineGlyphs = ArrayList<PFreeTypeFont.ShapedGlyph>()
    var lineWidth = 0f
    var pen = 0f

    fun flush() {
      if (lineGlyphs.isNotEmpty()) out.add(lineGlyphs)
      lineGlyphs = ArrayList()
      lineWidth = 0f
      pen = 0f
    }

    for (token in tokenize(para)) {
      val tokenGlyphs = font.shape(token)
      val tokenWidth = font.width(tokenGlyphs) * fontScaleX
      if (lineGlyphs.isNotEmpty() && lineWidth + tokenWidth > maxW) flush()
      // Offset each token's glyphs to be relative to the current line start.
      for (g in tokenGlyphs) lineGlyphs.add(offsetGlyph(g, pen))
      pen += tokenWidth
      lineWidth += tokenWidth
    }
    flush()
    return out
  }

  private fun offsetGlyph(g: PFreeTypeFont.ShapedGlyph, dx: Float): PFreeTypeFont.ShapedGlyph =
    PFreeTypeFont.ShapedGlyph(g.glyph, g.cluster, g.x + dx, g.y, g.advanceX, g.advanceY)

  private fun tokenize(s: String): List<String> {
    val tokens = ArrayList<String>()
    var i = 0
    while (i < s.length) {
      // whitespace run
      var j = i
      while (j < s.length && s[j].isWhitespace()) j++
      if (j > i) {
        tokens.add(s.substring(i, j))
        i = j
        continue
      }
      // word run
      j = i
      while (j < s.length && !s[j].isWhitespace()) j++
      if (j > i) tokens.add(s.substring(i, j))
      i = j
    }
    return tokens
  }

  private fun effAscent() = font.ascent * fontScaleY

  private fun effDescent() = font.descent * fontScaleY

  private fun effLineHeight() = font.lineHeight * fontScaleY

  private fun bgLeft() = background?.getLeftWidth() ?: 0f

  private fun bgRight() = background?.getRightWidth() ?: 0f

  private fun bgBottom() = background?.getBottomHeight() ?: 0f

  private fun bgTop() = background?.getTopHeight() ?: 0f

  // -----------------------------------------------------------------------------------
  // Drawing
  // -----------------------------------------------------------------------------------

  override fun draw() {
    validate()
    if (font == null) return

    val alpha = color.a * parentAlpha

    if (background != null) {
      Draw.color(color.r, color.g, color.b, alpha)
      background!!.draw(x, y, width, height)
    }

    if (text.isEmpty()) return

    tempTextColor.set(color)
    tempTextColor.a = alpha
    fontColor?.let { tempTextColor.mul(it) }
    Draw.color(tempTextColor)
    val basePacked = Draw.getColorPacked()

    val l = measure()
    if (l.isEmpty()) return

    val contentW = width - bgLeft() - bgRight()
    val contentH = height - bgBottom() - bgTop()
    val contentX = x + bgLeft()
    val contentY = y + bgBottom()
    val lineHeight = effLineHeight()
    val totalH = l.size * lineHeight

    val firstBaseline =
      when {
        (labelAlign and Align.top) != 0 -> contentY + contentH - effAscent()
        (labelAlign and Align.bottom) != 0 -> contentY + (contentH - totalH) + effAscent()
        else -> contentY + (contentH - totalH) / 2f + effAscent()
      }

    for (li in l.indices) {
      val line = l[li]
      val baseline = firstBaseline - li * lineHeight
      var pen =
        when {
          (lineAlign and Align.right) != 0 -> contentX + contentW - line.width
          (lineAlign and Align.center) != 0 -> contentX + (contentW - line.width) / 2f
          else -> contentX
        }
      for (piece in line.pieces) {
        val packed =
          if (piece.color == null) basePacked
          else packColor(tempTextColor, piece.color)
        font.draw(piece.glyphs, pen, baseline, packed, 0, fontScaleX, fontScaleY)
        pen += piece.width
      }
    }
  }

  /** Packs `textColor * pieceColor` into a vertex color without touching [Draw]'s active color. */
  private fun packColor(textColor: Color, pieceColor: Color): Float {
    tempPieceColor.set(textColor).mul(pieceColor)
    return tempPieceColor.toFloatBits()
  }
}
