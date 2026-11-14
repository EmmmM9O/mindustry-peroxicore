package peroxicore.ciallo

import peroxicore.ciallo.value.*
import kotlin.test.*

class CialloConsoleTest {
  @Test
  fun scannerIgnoresCommentsAndStrings() {
    assertEquals(0, scanCialloSource("print \"{\" // }").braces)
    assertEquals(0, scanCialloSource("/* { } */").braces)
    assertEquals(1, scanCialloSource("print(\"x\"").parens)
  }

  @Test
  fun completerOffersFunctionsGlobalsAndFields() {
    val environment = CialloEnvironment(output = { })
    val engine = CialloEngine(environment)
    environment.global("alpha", CialloInt(1))
    environment.symbol("obj", CialloObj(linkedMapOf("field" to CialloInt(2))))
    val completer = CialloCompleter(environment, engine.globalScope)

    assertTrue(completer.candidatesFor("pri").contains("print"))
    assertTrue(completer.candidatesFor("@al").contains("@alpha"))
    assertTrue(completer.candidatesFor("obj.fi").contains("obj.field"))
    assertTrue(completer.candidatesFor("@obj.fi").contains("@obj.field"))
  }

  @Test
  fun highlighterKeepsText() {
    val environment = CialloEnvironment(output = { })
    val highlighter = CialloHighlighter(environment)
    val source = "print(1 + 2 == 3) // ok"
    val highlighted = highlighter.highlightSource(source)
    assertEquals(source, highlighted.toString())
    assertEquals(source.length, highlighted.length)
  }
}
