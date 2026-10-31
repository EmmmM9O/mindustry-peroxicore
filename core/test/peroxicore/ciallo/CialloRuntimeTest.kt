package peroxicore.ciallo

import kotlin.test.*

class CialloRuntimeTest {
  @Test
  fun environmentsHaveIndependentGlobals() {
    val runtime = CialloRuntime()
    val first = mutableListOf<String>()
    val second = mutableListOf<String>()
    runtime.create("first", CialloSandbox.SAFE) { first += it }
    runtime.create("second", CialloSandbox.SAFE) { second += it }

    runtime.execute("first", "@x mount 1\nprint @x")
    runtime.execute("second", "@x mount 2\nprint @x")
    runtime.execute("first", "print @x")
    runtime.execute("second", "print @x")

    assertEquals(listOf("1", "1"), first)
    assertEquals(listOf("2", "2"), second)
  }

  @Test
  fun environmentOwnsGlobalsAcrossEngines() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    CialloEngine(environment).execute("@x mount 1")
    CialloEngine(environment).execute("print @x")
    assertEquals(listOf("1"), lines)
  }

  @Test
  fun environmentGlobalsCanBeSeededByHost() {
    val lines = mutableListOf<String>()
    val environment = CialloEnvironment(output = { lines += it })
    environment.global("x", CialloValue.IntValue(11))
    CialloEngine(environment).execute("print @x")
    assertEquals(listOf("11"), lines)
    assertEquals(CialloValue.IntValue(11), environment.global("x"))
  }

  @Test
  fun environmentsKeepTheirOwnSandbox() {
    val runtime = CialloRuntime()
    val safeOutput = mutableListOf<String>()
    val fullOutput = mutableListOf<String>()
    val safe = runtime.create("safe", CialloSandbox.SAFE) { safeOutput += it }
    val full = runtime.create("full", CialloSandbox.FULL) { fullOutput += it }

    safe.symbol("host", CialloValue.JavaValue(CialloReflectionTarget()))
    full.symbol("host", CialloValue.JavaValue(CialloReflectionTarget()))

    assertFailsWith<CialloSandboxException> {
      CialloEngine(safe).execute("print(@host.value)")
    }

    CialloEngine(full).execute("print(@host.value)\nprint(@host.double(3))")
    assertTrue(safeOutput.isEmpty())
    assertEquals(listOf("7", "6"), fullOutput)
  }

  @Test
  fun environmentNamesAreUnique() {
    val runtime = CialloRuntime()
    runtime.create("main")
    assertFailsWith<CialloRuntimeException> { runtime.create("main") }
    assertFailsWith<CialloRuntimeException> { runtime.environment("missing") }
    assertEquals(setOf("main"), runtime.names())
    assertTrue(runtime.remove("main"))
    assertFalse(runtime.names().contains("main"))
  }
}
