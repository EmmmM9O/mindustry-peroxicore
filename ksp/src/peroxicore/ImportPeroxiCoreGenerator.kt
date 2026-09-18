package peroxicore

import arc.util.serialization.*
import com.google.auto.service.*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import peroxicore.annotations.*
import peroxicore.generator.*

data class ImportPeroxiCoreMeta(
  val peroxicoreLoader: Boolean,
) {
  companion object {
    fun parse(anno: PXCAnnotatin) =
      anno.run {
        ImportPeroxiCoreMeta(
          peroxicoreLoader = bool("peroxicoreLoader", true)
        )
      }
  }
}

class ImportPeroxiCoreProcessor(
  codeGenerator: CodeGenerator,
  logger: KSPLogger,
  options: Map<String, String>,
) : BaseProcessor(codeGenerator, logger, options) {
  val json = Json(JsonWriter.OutputType.json)

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val (symbols, invalids) = resolver.symbols<ImportPeroxiCore>(true)
    if (symbols.isEmpty()) {
      return emptyList()
    }
    if (symbols.size != 1) {
      logger.warn(
        "Find too more @ImportPeroxiCore .We will only take the first.\nNumber: ${symbols.size}\n${symbols.joinToString(
          separator = "\n"
        ){ it.locate() }}"
      )
    }
    val mod = symbols.first() as KSClassDeclaration
    val config = mod.annotationNotNull<ImportPeroxiCore>().toPXC().let(ImportPeroxiCoreMeta::parse)
    val data = json.toJson(config)
    if (logConfigs) {
      info("@ImportPeroxiCore  with config\n$data")
    }
    codeGenerator
      .createNewFile(
        Dependencies(false, mod.containingFile!!),
        packageName = "",
        fileName = "peroxicore",
        extensionName = "json"
      ).bufferedWriter()
      .use { writer ->
        writer.write(data)
      }
    return invalids
  }
}

@AutoService(SymbolProcessorProvider::class)
class ImportPeroxiCoreProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment) =
    ImportPeroxiCoreProcessor(environment.codeGenerator, environment.logger, environment.options)
}
