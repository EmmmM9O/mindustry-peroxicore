package peroxicore.ui

import arc.graphics.*
import kotlin.test.*

class LabelCodeTextFactoryTest {
  private val factory = LabelCodeTextFactory()

  private fun tag(color: Color): String {
    val digits = Integer.toHexString(color.rgb888())
    return "[#" + "0".repeat(6 - digits.length) + digits + "]"
  }

  @Test
  fun escapesBracketsWhenUnhighlighted() {
    // Only a lone '[' starts markup in arc, so ']' is ordinary text and needs no escape.
    assertEquals("a[[b]c", factory.markedUp("a[b]c", emptyList()))
  }

  @Test
  fun wrapsTokensInColorTags() {
    val code = "set x"
    val tokens = listOf(CodeToken(0, 3, CodeTokenType.KEYWORD))
    val marked = factory.markedUp(code, tokens)

    assertEquals(tag(factory.palette.keyword!!) + "set[] x", marked)
  }

  @Test
  fun escapesBracketsInsideTokens() {
    val code = "[a]"
    val tokens = listOf(CodeToken(0, 3, CodeTokenType.STRING))
    assertEquals(tag(factory.palette.string!!) + "[[a][]", factory.markedUp(code, tokens))
  }

  @Test
  fun keepsUncoloredGaps() {
    val code = "a + b"
    val tokens = listOf(CodeToken(2, 3, CodeTokenType.OPERATOR))
    assertEquals("a " + tag(factory.palette.operator!!) + "+[] b", factory.markedUp(code, tokens))
  }

  @Test
  fun defaultPaletteIsTokyoNight() {
    val palette = CodePalette()
    assertEquals(Color.valueOf("1a1b26ff"), palette.background)
    assertEquals(Color.valueOf("a9b1d6ff"), palette.foreground)
    assertEquals(Color.valueOf("bb9af7ff"), palette.keyword)
  }

  @Test
  fun moonlightPresetMatchesMoonlightColors() {
    val palette = CodePalette.moonlight()
    assertEquals(Color.valueOf("222436ff"), palette.background)
    assertEquals(Color.valueOf("c8d3f5ff"), palette.foreground)
    assertEquals(Color.valueOf("af9fffff"), palette.keyword)
    assertEquals(Color.valueOf("7af8caff"), palette.string)
  }

  @Test
  fun gutterNumbersEveryLine() {
    assertEquals("1", factory.gutterText(1))
    assertEquals("1\n2\n3", factory.gutterText(3))
    // An empty source still has one (empty) line.
    assertEquals("1", factory.gutterText(0))
  }
}
