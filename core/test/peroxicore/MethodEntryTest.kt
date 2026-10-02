package peroxicore

import peroxicore.annotations.*
import kotlin.test.*

class MethodEntryTest {
  interface TestInjectComp {
    @MethodEntry("value", insert = InsertPosition.INJECT)
    fun valueInject(): Int = 630
  }

  open class TestInjectParent {
    open fun value(): Int = 529
  }

  class TestInject : TestInjectParent(), TestInjectComp {
  }

  @Test
  fun injectTest() {
    assertEquals(630, TestInject().value(), "Inject fail for TestInject")
  }
}