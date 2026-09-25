package peroxicore.compiler

import com.google.auto.service.*
import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fir.extensions.*
import peroxicore.compiler.fir.*
import peroxicore.compiler.ir.*

@ExperimentalCompilerApi
@AutoService(CompilerPluginRegistrar::class)
class POCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String
    get() = "peroxicore.complier"
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector =
      configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE]
    FirExtensionRegistrarAdapter.registerExtension(POFirExtensionRegistrar(messageCollector))
    IrGenerationExtension.registerExtension(POIrGenerationExtension(messageCollector))
  }
}
