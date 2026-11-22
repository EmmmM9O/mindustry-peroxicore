package peroxicore

import arc.*
import arc.graphics.*
import arc.scene.*
import arc.scene.ui.layout.*
import arc.util.Align
import peroxicore.ui.*

/**
 * A [CodeTextFactory] that renders code through [PLabel], i.e. with LWJGL FreeType + HarfBuzz
 * shaping instead of arc's bitmap fonts.
 *
 * Because shaping runs through HarfBuzz, ligatures inside a run are honoured ("->", "=>", "!=",
 * "ffi", …) as long as the font provides them, which is not possible with arc's per-character
 * [arc.graphics.g2d.Font] pipeline. Highlight tokens become colored [PLabel.Segment]s, so ligatures
 * survive within each token and colors never rely on arc's markup.
 *
 * By default the JetBrains Mono font bundled with the UniverseKit markdown module is used; pass
 * [fontProvider] (or use the path constructor) to supply another face. The face is created lazily
 * on first use and reused for every code block, so the glyph atlas is shared.
 */
open class PLabelCodeTextFactory @JvmOverloads constructor(
  var fontSize: Int = Scl.scl(19f).toInt(),
  override var palette: CodePalette = CodePalette(),
  private val fontProvider: () -> PFreeTypeFont = { PFreeTypeFont.load(bundledMonoBytes(), fontSize) },
) : CodeTextFactory {

  /** Convenience constructor loading the face from a font file path. */
  constructor(fontPath: String, fontSize: Int = Scl.scl(19f).toInt()) :
    this(fontSize, fontProvider = { PFreeTypeFont.load(fontPath, fontSize) })
  @Volatile private var face: PFreeTypeFont? = null

  /** The shared shaped font face, created on demand. */
  fun font(): PFreeTypeFont = face ?: synchronized(this) {
    face ?: fontProvider().also { face = it }
  }

  override fun create(code: String, tokens: List<CodeToken>, fontScale: Float): Element =
    PLabel(font(), code).apply {
      wrap = false
      setFontScale(fontScale)
      palette.foreground?.let { fontColor = it }
      if (tokens.isNotEmpty()) setSegments(segmentsOf(code, tokens))
    }

  /**
   * Line numbers in the same shaped font and scale as [create], so they line up with the code.
   * Shaped as digits only, so no ligature can make a number span two lines.
   */
  override fun gutter(lineCount: Int, fontScale: Float): Element =
    PLabel(font(), (1..lineCount).joinToString("\n")).apply {
      wrap = false
      setFontScale(fontScale)
      palette.comment?.let { fontColor = it }
      setAlignment(Align.right, Align.right)
    }

  /** Splits [code] into colored [PLabel.Segment]s following [tokens]. */
  fun segmentsOf(code: String, tokens: List<CodeToken>): List<PLabel.Segment> {
    val out = ArrayList<PLabel.Segment>(tokens.size * 2 + 1)
    var cursor = 0
    for (token in tokens) {
      if (token.start > cursor) out.add(PLabel.Segment(code.substring(cursor, token.start)))
      out.add(PLabel.Segment(code.substring(token.start, token.end), palette.colorOf(token.type)))
      cursor = token.end
    }
    if (cursor < code.length) out.add(PLabel.Segment(code.substring(cursor)))
    return out
  }

  /** Releases the shared atlas; the factory must not be used afterwards. */
  fun dispose() {
    synchronized(this) {
      face?.dispose()
      face = null
    }
  }

  /**
   * Markdown provider that renders fenced/indented code blocks through this factory, so a
   * [universe.ui.markdown.Markdown] document gets ligature-aware code blocks in one line.
   */
  fun markdownProvider(): PCodeBlockProvider = PCodeBlockProvider(this)

  companion object {
    /** Path of the JetBrains Mono face shipped inside the markdown module. */
    const val BUNDLED_MONO_PATH = "fonts/JetBrainsMono.ttf"

    private fun bundledMonoBytes(): ByteArray {
      PLabelCodeTextFactory::class.java.classLoader
        ?.getResourceAsStream(BUNDLED_MONO_PATH)
        ?.use { return it.readBytes() }
      // Internal assets are readable this way on both desktop and mobile.
      return Core.files.internal(BUNDLED_MONO_PATH).readBytes()
    }
  }
}
