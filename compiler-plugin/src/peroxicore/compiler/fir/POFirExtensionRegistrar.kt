package peroxicore.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.fir.extensions.*
import peroxicore.compiler.*

class POFirExtensionRegistrar(
  val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    // TODO
//    +ActionFirGenerator.factory(messageCollector)
    +AnnoMarker.factory(Annotations.MethodEntry, messageCollector)
    +EntryFirGenerator.factory(messageCollector)
  }
}
