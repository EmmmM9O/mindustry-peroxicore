package peroxicore

import arc.util.serialization.*
import com.google.auto.service.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import peroxicore.annotations.*

data class ModConfigMeta(
  var name: String?,
  var minGameVersion: String,
  var displayName: String,
  var author: String,
  var description: String,
  var version: String,
  var repo: String,
  var subtitle: String,
  var dependencies: List<String>,
  var softDependencies: List<String>,
  var pregenerated: Boolean,
  var hidden: Boolean,
  var keepOutlines: Boolean,
  var java: Boolean,
  var texturescale: Float,
  var contentOrder: List<String>,
  var legacyCompatible: Boolean,
  var main: String,
) {
  companion object {
    fun parse(anno: PXCAnnotatin) =
      anno.run {
        ModConfigMeta(
          name = stringNull("name"),
          minGameVersion = string("minGameVersion", ""),
          displayName = string("displayName", ""),
          author = string("author", ""),
          description = string("description", ""),
          version = string("version", ""),
          repo = string("repo", ""),
          subtitle = string("subtitle", ""),
          dependencies = stringArr("dependencies", emptyList()),
          softDependencies = stringArr("softDependencies", emptyList()),
          pregenerated = bool("pregenerated", false),
          hidden = bool("hidden", false),
          keepOutlines = bool("keepOutlines", false),
          java = bool("java", true),
          texturescale = float("texturescale", 1.0f),
          contentOrder = stringArr("contentOrder", emptyList()),
          legacyCompatible = bool("legacyCompatible", false),
          main = string("main", "")
        )
      }
  }
}

class ModConfigProcessor(
  codeGenerator: CodeGenerator,
  logger: KSPLogger,
  options: Map<String, String>,
) : BaseProcessor(codeGenerator, logger, options) {
  val json = Json(JsonWriter.OutputType.json)

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val (symbols, invalids) = resolver.symbols<ModConfig>(true)
    if (symbols.isEmpty()) {
      return emptyList()
    }
    if (symbols.size != 1) {
      logger.warn(
        "Find too more @ModConfig.We will only take the first.\nNumber: ${symbols.size}\n${
          symbols.joinToString(
            separator = "\n"
          ) { it.locate() }
        }"
      )
    }
    val mod = symbols.first() as KSClassDeclaration
    val config = mod.annotationNotNull<ModConfig>().toPXC().let(ModConfigMeta::parse)
    if (config.main.isEmpty()) config.main = mod.qualifiedName?.asString() ?: "ERROR"
    if (config.version.isEmpty()) config.version = modVersion
    if (config.subtitle.isEmpty()) config.subtitle = modSubtitle
    if (config.author.isEmpty()) config.author = modAuthor
    if (config.minGameVersion.isEmpty()) config.minGameVersion = minGameVersion
    if (config.repo.isEmpty()) config.repo = modRepo
    if (modHidden.isNotEmpty()) config.hidden = modHidden == "true"
    val data = json.toJson(config)
    if (logConfigs) {
      info("@ModConfig with config\n$data")
    }
    codeGenerator
      .createNewFile(
        Dependencies(false, mod.containingFile!!),
        packageName = "",
        fileName = "mod",
        extensionName = "json"
      ).bufferedWriter()
      .use { writer ->
        writer.write(data)
      }
    return invalids
  }

  val modVersion by lazy {
    options.getOrDefault(KspOptions.modVersion, "")
  }
  val modSubtitle by lazy {
    options.getOrDefault(KspOptions.modSubtitle, "")
  }
  val modAuthor by lazy {
    options.getOrDefault(KspOptions.modAuthor, "")
  }
  val minGameVersion by lazy {
    options.getOrDefault(KspOptions.minGameVersion, "")
  }
  val modRepo by lazy {
    options.getOrDefault(KspOptions.modRepo, "")
  }
  val modHidden by lazy {
    options.getOrDefault(KspOptions.modHidden, "")
  }
}

@AutoService(SymbolProcessorProvider::class)
class ModConfigProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment) =
    ModConfigProcessor(environment.codeGenerator, environment.logger, environment.options)
}
