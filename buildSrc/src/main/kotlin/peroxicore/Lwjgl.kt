package peroxicore

import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.gradle.api.*
import org.gradle.kotlin.dsl.*

fun Project.excludeLwjgl() {
  tasks {
    tasks.withType<ShadowJar> {
      dependencies {
        exclude(dependency("org.lwjgl:lwjgl"))
      }
    }
  }
}

fun Project.androidLwjgl(module: String) =
  fetchGithub("EmmmM9O", "lwjgl3", "android", "$module.jar")