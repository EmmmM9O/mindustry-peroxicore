package peroxicore.compiler

import org.jetbrains.kotlin.name.*

object Annotations {
  val MethodEntry = ClassId.topLevel(FqName("peroxicore.annotations.MethodEntry"))
  val Actionable = ClassId.topLevel(FqName("peroxicore.annotations.Actionable"))
  val ToAction = ClassId.topLevel(FqName("peroxicore.annotations.ToAction"))
}

object AnnoProps {
  val entryMethod = Name.identifier("entryMethod")
  val params = Name.identifier("params")
  val context = Name.identifier("context")
  val insert = Name.identifier("insert")
  val resType = Name.identifier("Res")
}
