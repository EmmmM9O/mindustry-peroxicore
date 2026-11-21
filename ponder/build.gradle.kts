dependencies {
  compileOnlyApi(project(":core"))
  implementation(Library.kotlinpoet)

  // Ciallo tests parse and format scripts only; the game classes are needed because the content
  // namespaces carry `mindustry.ctype.ContentType`.
  testImplementation(project(":core"))
  testImplementation(Library.mindustry)

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
//  kotlinCompilerPluginClasspath("com.github.emmmm9o.peroxicore:compiler-plugin:2.4.0-0.1.0")
}
