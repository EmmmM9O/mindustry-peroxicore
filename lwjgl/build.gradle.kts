import peroxicore.*

platformConfig()

dependencies {
  compileOnlyApi(project(":core"))
  compileOnlyApi(Library.lwjgl)
  implementation(Library.lwjglVersion("stb"))
  compileOnlyApi(project(":lwjgl:platform:expects"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  "shadowJvm"(project(":lwjgl:platform:desktop"))
  "shadowAndroid"(project(":lwjgl:platform:android"))
}
