package peroxicore.compiler

import org.jetbrains.kotlin.*

class POPluginKey(
  val feature: String,
) : GeneratedDeclarationKey() {
  override fun toString(): String = "Preoxide($feature)"
}

object PluginKeys {
  val methodEntry: GeneratedDeclarationKey = POPluginKey("MethodEntry")
  val actionable: GeneratedDeclarationKey = POPluginKey("Actionable")
}
