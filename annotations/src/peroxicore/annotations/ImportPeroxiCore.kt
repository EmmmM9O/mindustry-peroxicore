package peroxicore.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ImportPeroxiCore(
  val peroxicoreLoader: Boolean = true,
)
