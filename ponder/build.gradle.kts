dependencies {
  compileOnlyApi(project(":core"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
//  kotlinCompilerPluginClasspath("com.github.emmmm9o.peroxicore:compiler-plugin:2.4.0-0.1.0")
}
