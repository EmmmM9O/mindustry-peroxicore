pluginManagement {
  plugins {
    kotlin("jvm") version providers.gradleProperty("kotlinVersion").get()
  }
}

rootProject.name = "peroxicore"

include("core", "ponder", "annotations", "ksp", "compiler-plugin")
