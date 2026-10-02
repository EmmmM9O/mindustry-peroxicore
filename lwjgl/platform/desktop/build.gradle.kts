dependencies {
  compileOnlyApi(project(":core"))
  implementation(platform(Library.lwjglBom))
  implementation(Library.lwjgl)
  implementation(Library.lwjglModule("opengl"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
