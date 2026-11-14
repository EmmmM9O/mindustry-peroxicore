package peroxicore.ciallo

import peroxicore.ciallo.value.*
import kotlin.test.*

class JavaApplyTarget {
  @JvmField
  var value: Int = 4
}

class CialloObjectScopeTest {
  private fun environment(): CialloEnvironment = CialloEnvironment(output = { })

  @Test
  fun applyUsesObjectFields() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    environment.symbol("obj", CialloObj(linkedMapOf("x" to CialloInt(7))))
    CialloEngine(environment).execute("obj.apply{ print @x }")
    assertEquals(listOf("7"), lines)
  }

  @Test
  fun applyWritesBackToObject() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    val obj = CialloObj(linkedMapOf("x" to CialloInt(7)))
    environment.symbol("obj", obj)

    val result = CialloEngine(environment).execute("obj.apply{ @x set 8 }")
    assertSame(obj, result)
    assertEquals(CialloInt(8), obj.fields["x"])

    CialloEngine(environment).execute("print(@obj.x)")
    assertEquals(listOf("8"), lines)
  }

  @Test
  fun applyFallsBackToClosure() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    environment.symbol("obj", CialloObj(linkedMapOf("x" to CialloInt(1))))
    CialloEngine(environment).execute("@outer local 9\nobj.apply{ print @x\nprint @outer }")
    assertEquals(listOf("1", "9"), lines)
  }

  @Test
  fun applyUsesJavaFields() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it }, sandbox = CialloSandbox.FULL)
    val host = JavaApplyTarget()
    environment.symbol("host", CialloJavaValue(host))
    CialloEngine(environment).execute("host.apply{ @value set 5\nprint @value }")
    assertEquals(listOf("5"), lines)
    assertEquals(5, host.value)
  }

  @Test
  fun applyDuplicateLocalFails() {
    val environment = environment()
    environment.symbol("obj", CialloObj(linkedMapOf("x" to CialloInt(1))))
    assertFailsWith<CialloRuntimeException> {
      CialloEngine(environment).execute("obj.apply{ @x local 2 }")
    }
  }

  @Test
  fun nestedApplyResolvesInnerThenOuter() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    val inner = CialloObj(linkedMapOf("x" to CialloInt(2)))
    val outer =
      CialloObj(
        linkedMapOf(
          "y" to CialloInt(1),
          "inner" to inner,
        ),
      )
    environment.symbol("outer", outer)
    CialloEngine(environment).execute("outer.apply{ inner.apply{ print @x\nprint @y } }")
    assertEquals(listOf("2", "1"), lines)
  }
}
