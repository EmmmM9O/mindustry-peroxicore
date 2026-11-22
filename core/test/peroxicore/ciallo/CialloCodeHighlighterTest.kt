package peroxicore.ciallo

import peroxicore.ui.*
import kotlin.test.*

class CialloCodeHighlighterTest {
  private fun highlight(source: String) = CialloCodeHighlighter.highlight(source)

  private fun typeAt(source: String, needle: String): CodeTokenType? {
    val at = source.indexOf(needle)
    return highlight(source).firstOrNull { at >= it.start && at < it.end }?.type
  }

  @Test
  fun coversWholeSourceWithoutOverlap() {
    val source = "local x = @Blocks.duo 1.5s if(a != b) \"str\" // tail"
    val tokens = highlight(source)

    assertTrue(tokens.isNotEmpty())
    assertTrue(tokens.all { it.start in 0..source.length })
    assertTrue(tokens.all { it.end <= source.length && it.start < it.end })
    for (i in 1 until tokens.size) {
      assertTrue(tokens[i - 1].end <= tokens[i].start, "tokens must not overlap")
    }
  }

  @Test
  fun classifiesLiteralsAndComments() {
    val source = "set x 42 3.5 10s \"hi\" null true // done"
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "set"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "42"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "3.5"))
    assertEquals(CodeTokenType.NUMBER, typeAt(source, "10s"))
    assertEquals(CodeTokenType.STRING, typeAt(source, "\"hi\""))
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "null"))
    assertEquals(CodeTokenType.KEYWORD, typeAt(source, "true"))
    assertEquals(CodeTokenType.COMMENT, typeAt(source, "// done"))
  }

  @Test
  fun classifiesOperatorsAndCalls() {
    val source = "foo(1) + bar(x == y) - !z"
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "foo"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "bar"))
    assertEquals(CodeTokenType.OPERATOR, typeAt(source, "+"))
    assertEquals(CodeTokenType.OPERATOR, typeAt(source, "=="))
    assertEquals(CodeTokenType.OPERATOR, typeAt(source, "!"))
  }

  @Test
  fun classifiesAtSymbolPaths() {
    val source = "@Blocks.duo.print"
    val tokens = highlight(source)

    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "Blocks"))
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "duo"))
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "print"))
  }

  @Test
  fun parenlessCallHeadIsAFunction() {
    // `setBlock @Blocks.plastaniumCompressor` is `setBlock(@Blocks...)` without parens.
    val source = "setBlock @Blocks.plastaniumCompressor at(1, 1)"
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "setBlock"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "at"))
    assertEquals(CodeTokenType.SYMBOL, typeAt(source, "Blocks"))
  }

  @Test
  fun valueIdentifierAfterCallHeadIsNotAFunction() {
    val source = "config someValue"
    // `config` is not at a statement boundary as far as a value follows; only the head is a call.
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "config"))
    assertNull(typeAt(source, "someValue"))
  }

  @Test
  fun statementHeadsInEachLineAreFunctions() {
    val source = "world(11, 11) {\n  fillFloor @Blocks.stone\n}\nframe(1s) {\n  at(2, 5) build @Blocks.duo\n}"
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "world"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "fillFloor"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "frame"))
    assertEquals(CodeTokenType.FUNCTION, typeAt(source, "build"))
  }

  @Test
  fun malformedSourceStillHighlights() {
    val source = "local x = \"unterminated"
    assertTrue(highlight(source).isNotEmpty())
  }
}
