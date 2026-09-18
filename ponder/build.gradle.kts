dependencies {
  compileOnlyApi(project(":core"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
