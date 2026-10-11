import peroxicore.*

val native = if(!Config.buildNative) fetchNative("natives-android") else null

tasks.jar {
  if(native != null) from(native.map {zipTree(it)} )
}
