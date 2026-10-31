package peroxicore.ciallo

import java.io.*
import kotlin.test.*

class CialloFormatterTest {
  @Test
  fun formatsCallsAndBlocks() {
    val source =
      """
      then{label "a"}
      @x local [1,2, 3]
      build @Blocks.duo at(4,3) config null
      """.trimIndent()
    val expected =
      """
      then {
        label "a"
      }
      @x local [1, 2, 3]
      build @Blocks.duo at(4, 3) config null
      """.trimIndent()
    assertEquals(expected, CialloFormatter.format(source).trimEnd())
  }

  @Test
  fun formatsMathExpressions() {
    assertEquals(
      "print(1 + 2 * 3 == 7)\nprint(1 * (2 + 3))\n",
      CialloFormatter.format("print(1+2*3==7)\nprint(1*(2+3))"),
    )
  }

  @Test
  fun preservesComments() {
    val source =
      """
      // top
      @x local 1 // trailing
      @x local 2 /* inline */
      /* block */
      label @x
      """.trimIndent()
    assertEquals(source + "\n", CialloFormatter.format(source))
  }

  @Test
  fun formatsNestedBlocksAndBlankLines() {
    val source =
      """
      world {
        size(11, 11)

        fill @Blocks.green
        frame(1s) {
          label "x"
        }
      }
      """.trimIndent()
    assertEquals(source + "\n", CialloFormatter.format(source))
  }

  @Test
  fun formatsEmptyBlock() {
    assertEquals("then {}\n", CialloFormatter.format("then{}"))
  }

  @Test
  fun supportsCustomIndent() {
    assertEquals(
      "then {\n    label \"x\"\n}\n",
      CialloFormatter.format("then{label \"x\"}", "    "),
    )
  }

  @Test
  fun formattingIsIdempotent() {
    val source =
      """
      @x local 1
      then{ label @x at(1,2) }
      """.trimIndent()
    val once = CialloFormatter.format(source)
    assertEquals(once, CialloFormatter.format(once))
  }

  @Test
  fun invalidSourceFails() {
    assertFailsWith<CialloSyntaxException> {
      CialloFormatter.format("then {")
    }
  }

  @Test
  fun cliFormatsFileInPlace() {
    val file = File.createTempFile("ciallo", ".ciallo")
    try {
      file.writeText("then{label \"x\"}")
      assertEquals(0, CialloCli.run(arrayOf("fmt", "--write", file.path)))
      assertEquals("then {\n  label \"x\"\n}\n", file.readText())
    } finally {
      file.delete()
    }
  }
}
