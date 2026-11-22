package peroxicore.ui

import kotlin.test.*

class KotlinCodeHighlighterTest {
  private fun highlight(source: String) = KotlinCodeHighlighter.highlight(source)

  private fun typeAt(source: String, needle: String): CodeTokenType? {
    val at = source.indexOf(needle)
    return highlight(source).firstOrNull { at >= it.start && at < it.end }?.type
  }

  @Test
  fun coversSourceWithoutOverlap() {
    val source =
      """
      package demo

      /** Doc. */
      class Foo(val x: Int) {
        fun bar(y: String): List<Int> = listOf(y.length)
      }
      """.trimIndent()
    val tokens = highlight(source)

    assertTrue(tokens.isNotEmpty())
    assertTrue(tokens.all { it.end <= source.length && it.start < it.end })
    for (i in 1 until tokens.size) {
      assertTrue(tokens[i - 1].end <= tokens[i].start, "tokens must not overlap")
    }
  }

  @Test
  fun classifiesKeywordsAndLiterals() {
    val source = """val s = "text" + 'c' + 42 + 3.5f + 0xFF + true + null"""
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "val"))
    assertEquals(CodeTokenType.STRING, typeAt(source, "\"text\""))
    assertEquals(CodeTokenType.STRING, typeAt(source, "'c'"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "42"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "3.5f"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "0xFF"))
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "true"))
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "null"))
  }

  @Test
  fun classifiesComments() {
    val source = "// line\n/* block */ val a = 1"
    assertEquals(CodeTokenType.COMMENT, typeAt(source, "// line"))
    assertEquals(CodeTokenType.COMMENT, typeAt(source, "/* block */"))
  }

  @Test
  fun classifiesFunctionsAndTypes() {
    val source = "fun compute(x: Int): String = helper(x)"
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "fun"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "compute"))
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "Int"))
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "String"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "helper"))
  }

  @Test
  fun classifiesMemberAccessAndAnnotations() {
    val source = "@Test fun a() = obj.member"
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "Test"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "a"))
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "member"))
  }

  @Test
  fun classifiesMultiLineStrings() {
    val source = "val s = \"\"\"\n  raw text\n\"\"\""
    assertEquals(CodeTokenType.STRING, typeAt(source, "raw text"))
  }

  @Test
  fun stringInterpolationSplitsAroundExpression() {
    // Escaped so the Kotlin compiler does not interpolate it before the test sees it.
    val source = "val s = \"x \${1 + 2} y\""
    val tokens = highlight(source)
    // The interpolated expression is lexed in the default mode, so `1` and `2` are numbers inside
    // an otherwise string-colored literal.
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "1"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "2"))
    assertTrue(tokens.isNotEmpty())
  }

  @Test
  fun malformedSourceStillHighlights() {
    val source = "class Broken(val x: Int"
    assertTrue(highlight(source).isNotEmpty())
  }
}
