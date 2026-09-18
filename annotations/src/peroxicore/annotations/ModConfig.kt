package peroxicore.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ModConfig(
  val name: String,
  val minGameVersion: String = "",
  val displayName: String = "",
  val author: String = "",
  val description: String = "",
  val version: String = "",
  val repo: String = "",
  val subtitle: String = "",
  val dependencies: Array<String> = [],
  val softDependencies: Array<String> = [],
  val pregenerated: Boolean = false,
  val hidden: Boolean = false,
  val keepOutlines: Boolean = false,
  val java: Boolean = true,
  val texturescale: Float = 1.0f,
  val contentOrder: Array<String> = [],
  val legacyCompatible: Boolean = false,
  val main: String = "",
)
