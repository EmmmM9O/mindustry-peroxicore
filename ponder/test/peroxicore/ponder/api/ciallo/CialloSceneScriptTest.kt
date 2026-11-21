package peroxicore.ponder.api.ciallo

import peroxicore.ciallo.*
import kotlin.test.*

/**
 * Syntax-level checks for the shipped Ciallo demo scene.
 *
 * The tests deliberately stay on the parser/formatter: building a scene needs a
 * live Mindustry run (content loader, world, renderer), which a unit test cannot
 * provide.  What they do guarantee is that the documented script shape parses and
 * is already in canonical form, so `ciallo fmt -c` would not rewrite it.
 */
class CialloSceneScriptTest {
  /** Gradle runs tests with the module directory as the working directory. */
  private fun demo(): String {
    val file = java.io.File("assets/scripts/demo.ciallo")
    assertTrue(file.isFile, "missing ${file.absolutePath}")
    return file.readText()
  }

  @Test
  fun demoScriptParses() {
    // Throws CialloSyntaxException on any grammar error.
    CialloFormatter.format(demo())
  }

  @Test
  fun demoScriptIsCanonical() {
    val source = demo()
    assertEquals(source, CialloFormatter.format(source))
  }

  @Test
  fun sceneChainUsesTheDocumentedShapes() {
    val script =
      """
      @Blocks.itemSource scene {
        tag "blocks"
        duration 10s
        world(11, 11) {
          fillFloor @Blocks.stone
        }

        frame(1s) {
          at(2, 5) build @Blocks.itemSource
          build @Blocks.itemSource config @Items.copper at(4, 4)
          at(5, 5) showConfig()
        }
      }
      """.trimIndent()
    assertEquals(script + "\n", CialloFormatter.format(script + "\n"))
  }

  @Test
  fun timeLiteralsInsideFramesAreAccepted() {
    for (tick in listOf("1s", "60t", "1m", "2h", "1.5s", "30")) {
      val source = "@Blocks.duo scene {\n  frame($tick) {\n    full()\n  }\n}\n"
      assertNotNull(CialloFormatter.format(source), tick)
    }
  }
}
