package peroxicore.complier

import org.jetbrains.kotlin.name.*

object Annotations {
  val MethodEntry = ClassId.topLevel(FqName("peroxicore.annotations.MethodEntry"))
}

object AnnoProps {
  val entryMethod = Name.identifier("entryMethod")
  val params = Name.identifier("params")
  val context = Name.identifier("context")
  val insert = Name.identifier("insert")
}
