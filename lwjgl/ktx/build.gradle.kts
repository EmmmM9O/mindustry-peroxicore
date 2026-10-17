import peroxicore.*

platformConfig()

excludeLwjgl()

val lwjglKTX= androidLwjgl("lwjgl-natives-ktx")

dependencies {
  compileOnlyApi(project(":lwjgl"))

  compileOnly(Library.lwjgl)
  implementation(Library.lwjglVersion("ktx"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  "shadowAndroid"(lwjglKTX)
}
