pluginManagement {
  plugins {
    kotlin("jvm") version providers.gradleProperty("kotlinVersion").get()
  }
}

rootProject.name = "peroxicore"

include(
  ":core",
  ":ponder",
  ":annotations",
  ":ksp",
  ":compiler-plugin",
  ":lwjgl",
  ":lwjgl:generator",
  ":lwjgl:platform:expects",
  ":lwjgl:platform:android",
  ":lwjgl:platform:desktop",
  ":lwjgl:opus",
  ":lwjgl:ktx",
  ":lwjgl:font",
  ":lwjgl:natives",
  ":lwjgl:natives:natives-android",
)
