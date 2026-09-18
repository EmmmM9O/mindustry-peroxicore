package peroxicore.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class PeroxiCoreConfig(
  val packageName: String = "peroxicore.gen",
  val config: RemoteConfig = RemoteConfig(),
)

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RemoteConfig(
  val callName: String = "PCall",
  val packetName: String = "{}CallPacket",
)
