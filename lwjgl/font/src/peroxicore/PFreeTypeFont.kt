package peroxicore

import arc.graphics.*
import arc.graphics.g2d.*
import arc.util.*
import java.nio.*
import java.util.*
import org.lwjgl.system.*
import org.lwjgl.util.freetype.*
import org.lwjgl.util.harfbuzz.*

/**
 * A real-time TrueType/OpenType text renderer backed directly by LWJGL FreeType for rasterisation
 * and HarfBuzz for text shaping. It is intentionally independent of arc's freetype / BitmapFont
 * system.
 *
 * Glyphs are rasterised on demand into a single texture atlas (shelf packing). Shaping is done per
 * line so that OpenType features such as ligatures ("fi", "ff", …), kerning and complex-script
 * reordering are honoured.
 *
 * @param size the requested pixel size (em size). FreeType is configured via FT_Set_Pixel_Sizes.
 * @param fontBytes a direct heap-independent buffer holding the raw font file contents
 */
class PFreeTypeFont
private constructor(
  private val library: Long,
  private val face: FT_Face,
  private val hbFont: Long,
  private val hbBuffer: Long,
  //	private val fontBytes: ByteBuffer,
  private val atlasTexture: Texture,
  private val atlasWidth: Int,
  private val atlasHeight: Int,
  private val atlasPadding: Int,
  val size: Int,
  val ascent: Float,
  val descent: Float,
  val lineHeight: Float,
  val capHeight: Float,
  val xPpem: Int,
) : Disposable {

  // ---- glyph cache ----
  private class GlyphData(
    val texture: Texture,
    val u: Float,
    val v: Float,
    val u2: Float,
    val v2: Float,
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int,
  )

  private val glyphCache = HashMap<Int, GlyphData>()

  // vertex scratch buffer: (xy + color + uv + mixColor) * 4 verts = 24 floats per glyph
  private var verts = FloatArray(6 * 4 * 64)

  // shelf-packing cursor
  private var curbX = 0
  private var curbY = 0
  private var shelfHeight = 0

  /** The texture backing the glyph atlas. All glyph regions reference this texture. */
  val texture: Texture
    get() = atlasTexture

  // -----------------------------------------------------------------------------------
  // Public shaping / measurement
  // -----------------------------------------------------------------------------------

  /** One placed glyph in a shaped line, in pixel units relative to the line origin (baseline). */
  class ShapedGlyph(
    val glyph: Int,
    val cluster: Int,
    val x: Float,
    val y: Float,
    val advanceX: Float,
    val advanceY: Float,
  )

  /** Shapes a single logical line (must not contain newlines) and returns its placed glyphs. */
  fun shape(text: CharSequence): List<ShapedGlyph> {
    if (text.isEmpty()) return emptyList()
    val utf8 = encodeUtf8(text)
    try {
      val out = ArrayList<ShapedGlyph>(32)
      HarfBuzz.hb_buffer_clear_contents(hbBuffer)
      HarfBuzz.hb_buffer_add_utf8(hbBuffer, utf8, 0, utf8.remaining())
      HarfBuzz.hb_buffer_guess_segment_properties(hbBuffer)
      HarfBuzz.hb_shape(hbFont, hbBuffer, null)

      val infos = HarfBuzz.hb_buffer_get_glyph_infos(hbBuffer)
      val positions = HarfBuzz.hb_buffer_get_glyph_positions(hbBuffer)
      if (infos == null || positions == null) return emptyList()

      var penX = 0.0f
      var penY = 0.0f
      for (i in 0 until infos.capacity()) {
        val info = infos[i]
        val pos = positions[i]
        val gx = penX + pos.x_offset() / 64.0f
        val gy = penY + pos.y_offset() / 64.0f
        out.add(
          ShapedGlyph(
            info.codepoint(),
            info.cluster(),
            gx,
            gy,
            pos.x_advance() / 64.0f,
            pos.y_advance() / 64.0f,
          )
        )
        penX += pos.x_advance() / 64.0f
        penY += pos.y_advance() / 64.0f
      }
      return out
    } finally {
      freeUtf8(utf8)
    }
  }

  /** Total horizontal advance (pixels) of a shaped line. */
  fun width(glyphs: List<ShapedGlyph>): Float {
    if (glyphs.isEmpty()) return 0f
    val last = glyphs[glyphs.size - 1]
    return last.x + last.advanceX
  }

  // -----------------------------------------------------------------------------------
  // Glyph rasterisation + atlas
  // -----------------------------------------------------------------------------------

  private fun glyphData(index: Int): GlyphData? {
    glyphCache[index]?.let {
      return it
    }

    val err = FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_RENDER)
    if (err != 0) return null

    val slot = face.glyph() ?: return null
    val bitmap = slot.bitmap()
    val bw = bitmap.width()
    val bh = bitmap.rows()
    val pitch = bitmap.pitch()
    val left = slot.bitmap_left()
    val top = slot.bitmap_top()

    val data: GlyphData =
      if (bw <= 0 || bh <= 0) {
        // Empty glyph (e.g. space). Draw nothing but keep it cached.
        GlyphData(atlasTexture, 0f, 0f, 0f, 0f, 0, 0, left, top)
      } else {
        val px = pack(bw, bh)
        if (px == null) {
          Log.err("PFreeTypeFont: glyph $index does not fit in atlas (${atlasWidth}x$atlasHeight)")
          return null
        }
        val gx = px[0] + atlasPadding
        val gy = px[1] + atlasPadding

        val glyphPixmap = Pixmap(bw, bh)
        fillGlyphPixmap(glyphPixmap, bitmap, bw, bh, pitch)
        atlasTexture.draw(glyphPixmap, gx, gy)
        glyphPixmap.dispose()

        val region = TextureRegion(atlasTexture, gx, gy, bw, bh)
        GlyphData(atlasTexture, region.u, region.v, region.u2, region.v2, bw, bh, left, top)
      }

    glyphCache[index] = data
    return data
  }

  /**
   * Appends one glyph quad to [buf] at offset [offset], anchored at the glyph origin
   * [originX]/[originY].
   */
  private fun appendGlyph(
    buf: FloatArray,
    offset: Int,
    g: GlyphData,
    originX: Float,
    originY: Float,
    colorPacked: Float,
    scaleX: Float,
    scaleY: Float,
  ): Int {
    if (g.width <= 0 || g.height <= 0) return 0

    val leftX = originX + g.left * scaleX
    val rightX = leftX + g.width * scaleX
    val topY = originY + g.top * scaleY
    val bottomY = topY - g.height * scaleY

    var i = offset
    buf[i++] = leftX
    buf[i++] = bottomY
    buf[i++] = colorPacked
    buf[i++] = g.u
    buf[i++] = g.v2
    buf[i++] = 0f
    buf[i++] = leftX
    buf[i++] = topY
    buf[i++] = colorPacked
    buf[i++] = g.u
    buf[i++] = g.v
    buf[i++] = 0f
    buf[i++] = rightX
    buf[i++] = topY
    buf[i++] = colorPacked
    buf[i++] = g.u2
    buf[i++] = g.v
    buf[i++] = 0f
    buf[i++] = rightX
    buf[i++] = bottomY
    buf[i++] = colorPacked
    buf[i++] = g.u2
    buf[i++] = g.v2
    buf[i++] = 0f
    return 24
  }

  /**
   * Draws [glyphs] (as returned by [shape], presumed already made relative to the line origin) at
   * the given [baseX]/[baseY] baseline in world units with the supplied packed tint color. Glyphs
   * are uploaded into the active sprite batch in a single call. [scaleX]/[scaleY] scale the text
   * size around the baseline (a glyph's advance and bitmap are multiplied by the scale).
   */
  fun draw(
    glyphs: List<ShapedGlyph>,
    baseX: Float,
    baseY: Float,
    colorPacked: Float,
    start: Int = 0,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
  ) {
    if (glyphs.isEmpty()) return
    var count = start
    for (i in 0 until glyphs.size) {
      val shaped = glyphs[i]
      val g = glyphData(shaped.glyph) ?: continue
      if (count + 24 > verts.size) verts = verts.copyOf((verts.size + 24) * 2)
      count +=
        appendGlyph(
          verts,
          count,
          g,
          baseX + shaped.x * scaleX,
          baseY + shaped.y * scaleY,
          colorPacked,
          scaleX,
          scaleY,
        )
    }
    if (count > start) {
      Draw.vert(atlasTexture, verts, 0, count)
    }
  }

  // -----------------------------------------------------------------------------------
  // Internals
  // -----------------------------------------------------------------------------------

  /** Shelf-packs a [w]x[h] (already padded) region, returning the atlas destination [x, y]. */
  private fun pack(w: Int, h: Int): IntArray? {
    val paddedW = w + atlasPadding * 2
    val paddedH = h + atlasPadding * 2
    if (paddedW > atlasWidth || paddedH > atlasHeight) return null

    if (curbX + paddedW > atlasWidth) {
      curbX = 0
      curbY += shelfHeight + atlasPadding
      shelfHeight = 0
    }
    if (curbY + paddedH > atlasHeight) return null

    val result = intArrayOf(curbX, curbY)
    curbX += paddedW + atlasPadding
    if (paddedH > shelfHeight) shelfHeight = paddedH
    return result
  }

  private fun encodeUtf8(text: CharSequence): ByteBuffer {
    val bytes = text.toString().toByteArray(Charsets.UTF_8)
    val buf = MemoryUtil.memAlloc(bytes.size)
    buf.put(bytes)
    buf.flip()
    return buf
  }

  private fun freeUtf8(buf: ByteBuffer) {
    MemoryUtil.memFree(buf)
  }

  /**
   * Copies an 8-bit coverage FreeType bitmap into [pixmap] as white glyphs (r=g=b=255,
   * alpha=coverage).
   */
  private fun fillGlyphPixmap(pixmap: Pixmap, bitmap: FT_Bitmap, bw: Int, bh: Int, pitch: Int) {
    val absPitch = if (pitch < 0) -pitch else pitch
    if (absPitch == 0) return
    val src = bitmap.buffer(absPitch * bh) ?: return
    val pixels = pixmap.pixels
    for (y in 0 until bh) {
      val rowBase = y * absPitch
      // -0x100 == 0xFFFFFF00 as a signed 32-bit int: a fully white RGB (255,255,255) with a
      // coverage-driven alpha in the lowest byte. Arc's Pixmap stores RGBA8888 big-endian.
      val white = -0x100
      for (x in 0 until bw) {
        val cov = src.get(rowBase + x).toInt() and 0xff
        pixels.putInt((x + y * bw) * 4, white or cov)
      }
    }
  }

  override fun dispose() {
    HarfBuzz.hb_buffer_destroy(hbBuffer)
    HarfBuzz.hb_font_destroy(hbFont)
    FreeType.FT_Done_Face(face)
    if (library != NULL) FreeType.FT_Done_FreeType(library)
    atlasTexture.dispose()
  }

  companion object {
    private const val NULL = 0L

    @JvmStatic
    fun load(
      path: String,
      size: Int,
      atlasWidth: Int = 1024,
      atlasHeight: Int = 1024,
      atlasPadding: Int = 1,
    ): PFreeTypeFont {
      return MemoryStack.stackPush().use { stack ->
        val libPtr = stack.mallocPointer(1)
        val libErr = FreeType.FT_Init_FreeType(libPtr)
        if (libErr != 0)
          throw ArcRuntimeException("PFreeTypeFont: FT_Init_FreeType failed ($libErr)")
        val library = libPtr.get(0)

        val facePtr = stack.mallocPointer(1)
        val faceErr = FreeType.FT_New_Face(library, path, 0, facePtr)
        if (faceErr != 0) {
          FreeType.FT_Done_FreeType(library)
          throw ArcRuntimeException("PFreeTypeFont: FT_New_Memory_Face failed ($faceErr)")
        }
        val face = FT_Face.create(facePtr.get(0))

        val sizeErr = FreeType.FT_Set_Pixel_Sizes(face, 0, size)
        if (sizeErr != 0) {
          FreeType.FT_Done_Face(face)
          FreeType.FT_Done_FreeType(library)
          throw ArcRuntimeException("PFreeTypeFont: FT_Set_Pixel_Sizes failed ($sizeErr)")
        }

        val sizeStruct = face.size() ?: throw ArcRuntimeException("PFreeTypeFont: face has no size")
        val metrics = sizeStruct.metrics()
        val asc = metrics.ascender() / 64.0f
        val desc = metrics.descender() / 64.0f
        val lh = metrics.height() / 64.0f
        val ppem = metrics.x_ppem().toInt()

        val hbFont = HarfBuzz.hb_ft_font_create(face.address(), null)
        val hbBuffer = HarfBuzz.hb_buffer_create()

        val texture = Texture(atlasWidth, atlasHeight)
        texture.setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.linear)
        texture.setWrap(Texture.TextureWrap.clampToEdge, Texture.TextureWrap.clampToEdge)

        PFreeTypeFont(
          library,
          face,
          hbFont,
          hbBuffer,
          texture,
          atlasWidth,
          atlasHeight,
          atlasPadding,
          size,
          asc,
          desc,
          lh,
          capHeightOf(face, asc),
          ppem,
        )
      }
    }

    /** Approximate cap height from the glyph for 'A', falling back to the font ascent. */
    private fun capHeightOf(face: FT_Face, ascent: Float): Float {
      val capIdx = FreeType.FT_Get_Char_Index(face, 0x41)
      if (capIdx == 0) return ascent
      return if (FreeType.FT_Load_Glyph(face, capIdx, FreeType.FT_LOAD_DEFAULT) == 0)
        (face.glyph()?.metrics()?.height() ?: 0L) / 64.0f
      else ascent
    }
  }
}
