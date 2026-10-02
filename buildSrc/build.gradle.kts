import java.util.*

plugins {
  `kotlin-dsl`
}
val props = Properties()
file("../gradle.properties").inputStream().use { props.load(it) }
val kotlinVersion = props.getProperty("kotlinVersion")

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.3.0")
}

repositories {
  mavenLocal()
  mavenCentral()
  gradlePluginPortal()
}
