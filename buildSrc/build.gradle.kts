import java.util.Properties

plugins {
  `kotlin-dsl`
}

val props = Properties()
file("../gradle.properties").inputStream().use { props.load(it) }
val kotlinVersion = props.getProperty("kotlinVersion")

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}
