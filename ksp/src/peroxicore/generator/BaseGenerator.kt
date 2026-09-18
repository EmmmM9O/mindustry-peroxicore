package peroxicore.generator

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import peroxicore.*

abstract class BaseGenerator {
  lateinit var core: CoreProcessor

  fun setup(core: CoreProcessor) {
    this.core = core
  }

  abstract fun process(resolver: Resolver): List<KSAnnotated>
}

