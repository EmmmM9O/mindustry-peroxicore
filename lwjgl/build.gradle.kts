import peroxicore.*

platformConfig()

dependencies {
  compileOnlyApi(project(":core"))
  compileOnlyApi(Library.lwjgl)
  implementation(Library.lwjglVersion("stb"))
  implementation(Library.lwjglVersion("tinyexr"))
  implementation(Library.lwjglVersion("lz4"))
  implementation(Library.lwjglVersion("meshoptimizer"))
  implementation(Library.lwjglVersion("xxhash"))

  api("org.jspecify:jspecify:1.0.0")

  compileOnlyApi(project(":lwjgl:platform:expects"))

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  "shadowJvm"(project(":lwjgl:platform:desktop"))
  "shadowAndroid"(project(":lwjgl:platform:android"))
  "shadowAndroid"(project(":lwjgl:natives:natives-android"))
}

tasks.named("compileKotlin") {
  dependsOn(":lwjgl:generator:generate")
}
