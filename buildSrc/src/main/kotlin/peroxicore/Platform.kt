package peroxicore

import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.kotlin.dsl.*
import packageName

fun Project.platformConfig() {
  val shadowJvm: Configuration = configurations.create("shadowJvm") {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(configurations.getByName("implementation"))
  }
  val shadowAndroid: Configuration = configurations.create("shadowAndroid") {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(configurations.getByName("implementation"))
  }
  val mainOutput = the<SourceSetContainer>()
    .named("main")
    .map { it.output }

  tasks {
    named<ShadowJar>("shadowJar") {
      from("assets/") { include("**") }
      configurations = listOf(shadowAndroid)
      from(mainOutput)
    }
    register<ShadowJar>("shadowJarDesktop") {
      archiveFileName.set("${project.packageName()}-desktop.jar")
      from("assets/") { include("**") }
      configurations = listOf(shadowJvm)
      from(mainOutput)
    }
    named("d8Compile") {
      dependsOn.clear()
      dependsOn("shadowJar")
    }
    val pkgName = project.packageName()
    val dexJar = layout.buildDirectory.file("libs/$pkgName-dex.jar")
    named<Jar>("deploy") {
      dependsOn.clear()
      dependsOn("shadowJarDesktop", "d8Compile")
      exclude("**/*.class")
      archiveFileName.set("${project.packageName()}-android.jar")
    }
  }
}
