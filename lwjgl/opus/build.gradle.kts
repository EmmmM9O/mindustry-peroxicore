import peroxicore.*

platformConfig()
excludeLwjgl()

dependencies {
  compileOnlyApi(project(":lwjgl"))

  compileOnly(Library.lwjgl)
  implementation(Library.lwjglVersion("opus"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
