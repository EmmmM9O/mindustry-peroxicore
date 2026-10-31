package peroxicore.ciallo

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
    environment.symbol("obj", CialloValue.Obj(linkedMapOf("x" to CialloValue.IntValue(7))))
    CialloEngine(environment).execute("obj.apply{ print @x }")
    assertEquals(listOf("7"), lines)
  }

  @Test
  fun applyWritesBackToObject() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    val obj = CialloValue.Obj(linkedMapOf("x" to CialloValue.IntValue(7)))
    environment.symbol("obj", obj)

    val result = CialloEngine(environment).execute("obj.apply{ @x set 8 }")
    assertSame(obj, result)
    assertEquals(CialloValue.IntValue(8), obj.fields["x"])

    CialloEngine(environment).execute("print(@obj.x)")
    assertEquals(listOf("8"), lines)
  }

  @Test
  fun applyFallsBackToClosure() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    environment.symbol("obj", CialloValue.Obj(linkedMapOf("x" to CialloValue.IntValue(1))))
    CialloEngine(environment).execute("@outer local 9\nobj.apply{ print @x\nprint @outer }")
    assertEquals(listOf("1", "9"), lines)
  }

  @Test
  fun applyUsesJavaFields() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it }, sandbox = CialloSandbox.FULL)
    val host = JavaApplyTarget()
    environment.symbol("host", CialloValue.JavaValue(host))
    CialloEngine(environment).execute("host.apply{ @value set 5\nprint @value }")
    assertEquals(listOf("5"), lines)
    assertEquals(5, host.value)
  }

  @Test
  fun applyDuplicateLocalFails() {
    val environment = environment()
    environment.symbol("obj", CialloValue.Obj(linkedMapOf("x" to CialloValue.IntValue(1))))
    assertFailsWith<CialloRuntimeException> {
      CialloEngine(environment).execute("obj.apply{ @x local 2 }")
    }
  }

  @Test
  fun nestedApplyResolvesInnerThenOuter() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    val inner = CialloValue.Obj(linkedMapOf("x" to CialloValue.IntValue(2)))
    val outer =
      CialloValue.Obj(
        linkedMapOf(
          "y" to CialloValue.IntValue(1),
          "inner" to inner,
        ),
      )
    environment.symbol("outer", outer)
    CialloEngine(environment).execute("outer.apply{ inner.apply{ print @x\nprint @y } }")
    assertEquals(listOf("2", "1"), lines)
  }
}
