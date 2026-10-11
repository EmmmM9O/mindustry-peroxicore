import peroxicore.*

platformConfig()

excludeLwjgl()

val lwjglOpus = androidLwjgl("lwjgl-natives-opus")

dependencies {
  compileOnlyApi(project(":lwjgl"))

  compileOnly(Library.lwjgl)
  implementation(Library.lwjglVersion("opus"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  "shadowAndroid"(lwjglOpus)
}
