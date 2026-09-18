dependencies {
  compileOnlyApi(Library.mindustry)
  api(project(":annotations"))
  api(kotlin("stdlib-jdk8"))
  api(kotlin("reflect"))
  api("com.github.EB-wilson.UniverseKit:reflection:1.4")
  compileOnlyApi("com.github.EB-wilson.UniverseKit:platform:1.4")
  compileOnlyApi("com.github.EB-wilson.UniverseKit:expects:1.4")

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
