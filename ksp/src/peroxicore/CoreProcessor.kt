package peroxicore

import com.google.auto.service.*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import peroxicore.annotations.*
import peroxicore.generator.*

data class PeroxiCoreConfigMeta(
  val packageName: String,
  val remote: RemoteConfigMeta,
) {
  data class RemoteConfigMeta(
    val callName: String,
    val packetName: String,
  ) {
    companion object {
      fun parse(anno: PXCAnnotatin) =
        anno.run {
          RemoteConfigMeta(
            string("callName", "PCall"),
            string("packetName", "{}CallPacket")
          )
        }
    }
  }

  companion object {
    fun parse(anno: PXCAnnotatin) =
      anno.run {
        PeroxiCoreConfigMeta(
          string("packageName", "peroxicore.gen"),
          valueParse("remote", RemoteConfigMeta::parse)
        )
      }
  }
}

class CoreProcessor(
  codeGenerator: CodeGenerator,
  logger: KSPLogger,
  options: Map<String, String>,
) : BaseProcessor(codeGenerator, logger, options) {
  lateinit var config: PeroxiCoreConfigMeta
  var configSource: KSFile? = null
  val generators = mutableListOf<BaseGenerator>()

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (!::config.isInitialized) {
      val configAnnos = resolver.annotationPairs<PeroxiCoreConfig>()
      val pair: Pair<KSAnnotated?, KSAnnotation?> =
        when {
          configAnnos.isEmpty() -> {
            null to null
          }

          configAnnos.size == 1 -> {
            configAnnos.first()
          }

          else -> {
            logger.warn(
              "Find too more @PeroxiCoreConfig.We will only take the first.\nNumber: ${configAnnos.size}\n${configAnnos.joinToString(
                separator = "\n"
              ){ it.first.locate() }}"
            )
            configAnnos.first()
          }
        }
      configSource = pair.first?.containingFile
      config = pair.toPXC().let(PeroxiCoreConfigMeta::parse)
      if (logConfigs) {
        info("@PeroxiCore with config\n$config")
      }
      generators.forEach {
        it.setup(this)
      }
    }
    val invalids = mutableListOf<KSAnnotated>()
    generators.forEach {
      invalids += it.process(resolver)
    }
    return invalids
  }
}

@AutoService(SymbolProcessorProvider::class)
class CoreProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment) =
    CoreProcessor(environment.codeGenerator, environment.logger, environment.options)
}
