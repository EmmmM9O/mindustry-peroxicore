package peroxicore

import peroxicore.annotations.*
import kotlin.test.*

// Captures the execution order of injected @MethodEntry calls.
val trace = mutableListOf<String>()

fun resetTrace() {
  trace.clear()
}

class MethodEntryTest {
  interface TestInjectComp {
    @MethodEntry("value", insert = InsertPosition.INJECT)
    fun valueInject(): Int = 630
  }

  open class TestInjectBase {
    open fun value(): Int = 529
  }

  class TestInject : TestInjectBase(), TestInjectComp {
  }

  @Test
  fun injectTest() {
    assertEquals(630, TestInject().value(), "Inject fail for TestInject")
  }

  // --- HEAD / END / combined / order ---------------------------------------

  interface HeadComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
    fun entry() {
      trace += "HeadComp"
    }
  }

  open class HeadBase {
    open fun work() {
      trace += "HeadBase"
    }
  }

  class HeadImpl : HeadBase(), HeadComp

  @Test
  fun headInsertTest() {
    resetTrace()
    HeadImpl().work()
    assertEquals(listOf("HeadComp", "HeadBase"), trace, "HEAD should run before the base body")
  }

  interface EndComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.END)
    fun entry() {
      trace += "EndComp"
    }
  }

  open class EndBase {
    open fun work() {
      trace += "EndBase"
    }
  }

  class EndImpl : EndBase(), EndComp

  @Test
  fun endInsertTest() {
    resetTrace()
    EndImpl().work()
    assertEquals(listOf("EndBase", "EndComp"), trace, "END should run after the base body")
  }

  interface HeadEndHeadComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
    fun head() {
      trace += "head"
    }
  }

  interface HeadEndEndComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.END)
    fun end() {
      trace += "end"
    }
  }

  open class HeadEndBase {
    open fun work() {
      trace += "base"
    }
  }

  class HeadEndImpl : HeadEndBase(), HeadEndHeadComp, HeadEndEndComp

  @Test
  fun headAndEndTest() {
    resetTrace()
    HeadEndImpl().work()
    assertEquals(listOf("head", "base", "end"), trace, "HEAD runs first, base, then END")
  }

  interface MultiComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
    fun first() {
      trace += "first"
    }

    @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
    fun second() {
      trace += "second"
    }
  }

  open class MultiBase {
    open fun work() {
      trace += "base"
    }
  }

  class MultiImpl : MultiBase(), MultiComp

  @Test
  fun multipleHeadOrderTest() {
    resetTrace()
    MultiImpl().work()
    assertEquals(listOf("first", "second", "base"), trace, "Multiple HEAD entries keep declaration order")
  }

  // --- inherited component interface ---------------------------------------

  interface ParentComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
    fun entry() {
      trace += "injected"
    }
  }

  interface ChildComp : ParentComp

  open class InheritCompBase {
    open fun work() {
      trace += "base"
    }
  }

  class InheritCompImpl : InheritCompBase(), ChildComp

  @Test
  fun inheritedCompInterfaceTest() {
    resetTrace()
    InheritCompImpl().work()
    assertEquals(listOf("injected", "base"), trace, "Entries on a parent comp interface are inherited")
  }

  // --- overriding method with a real body -----------------------------------

  interface RealBodyComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
    fun entry() {
      trace += "comp"
    }
  }

  open class RealBodyBase {
    open fun work() {
      trace += "base"
    }
  }

  class RealBodyImpl : RealBodyBase(), RealBodyComp {
    override fun work() {
      trace += "real"
    }
  }

  @Test
  fun realBodyHeadTest() {
    resetTrace()
    RealBodyImpl().work()
    assertEquals(listOf("comp", "real"), trace, "HEAD runs before a real overridden body")
  }

  // --- positional parameter mapping ----------------------------------------

  open class IndexBase {
    open fun work(a: Int, b: Int): Int = a * b
  }

  interface IndexComp {
    // "1" and "2" skip the implicit receiver at index 0.
    @MethodEntry(entryMethod = "work", params = ["1", "2"], insert = InsertPosition.INJECT)
    fun transform(x: Int, y: Int): Int = x * 10 + y
  }

  class IndexImpl : IndexBase(), IndexComp

  @Test
  fun indexParamTest() {
    assertEquals(34, IndexImpl().work(3, 4), "Index params map to the target arguments")
  }

  // --- named parameter + super() mapping ------------------------------------

  open class NamedBase {
    open fun work(value: Int): Int = value + 1
  }

  interface NamedComp {
    @MethodEntry(entryMethod = "work", params = ["value", "super()"], insert = InsertPosition.INJECT)
    fun transform(value: Int, sup: Int): Int = value + sup
  }

  class NamedImpl : NamedBase(), NamedComp

  @Test
  fun namedParamTest() {
    assertEquals(21, NamedImpl().work(10), "super() resolves to the parent return value")
  }

  // --- context property mapping ---------------------------------------------

  open class ContextBase {
    open fun work() {
      trace += "base"
    }
  }

  interface ContextComp {
    @MethodEntry(entryMethod = "work", context = ["factor"], insert = InsertPosition.HEAD)
    fun entry(factor: Int) {
      trace += "factor=$factor"
    }
  }

  class ContextImpl : ContextBase(), ContextComp {
    val factor = 42
  }

  @Test
  fun contextParamTest() {
    resetTrace()
    ContextImpl().work()
    assertEquals(listOf("factor=42", "base"), trace, "context reads an owning-class property")
  }

  // --- OVERRIDE --------------------------------------------------------------

  open class OverrideBase {
    open fun work(): String = "base"
  }

  interface OverrideComp {
    @MethodEntry(entryMethod = "work", insert = InsertPosition.OVERRIDE)
    fun override(): String = "overridden"
  }

  class OverrideImpl : OverrideBase(), OverrideComp

  @Test
  fun overrideTest() {
    assertEquals("overridden", OverrideImpl().work(), "OVERRIDE replaces the inherited result")
  }

  // --- INJECT chain ----------------------------------------------------------

  open class InjectChainBase {
    open fun work(value: Int): Int = value * 2
  }

  interface InjectChainComp {
    @MethodEntry(entryMethod = "work", params = ["super()"], insert = InsertPosition.INJECT)
    fun plusTen(value: Int): Int = value + 10

    @MethodEntry(entryMethod = "work", params = ["super()"], insert = InsertPosition.INJECT)
    fun timesTwo(value: Int): Int = value * 2
  }

  class InjectChainImpl : InjectChainBase(), InjectChainComp

  @Test
  fun injectChainTest() {
    // work(1) = 2 -> +10 = 12 -> *2 = 24
    assertEquals(24, InjectChainImpl().work(1), "INJECT entries form a chain over the parent result")
  }
}
