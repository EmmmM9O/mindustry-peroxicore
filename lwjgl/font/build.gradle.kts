import peroxicore.*

platformConfig()

excludeLwjgl()

val lwjglFont = androidLwjgl("lwjgl-natives-font")

dependencies {
  compileOnlyApi(project(":lwjgl"))

  compileOnly(Library.lwjgl)
  implementation(Library.lwjglVersion("freetype"))
  implementation(Library.lwjglVersion("harfbuzz"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  "shadowAndroid"(lwjglFont)
}
