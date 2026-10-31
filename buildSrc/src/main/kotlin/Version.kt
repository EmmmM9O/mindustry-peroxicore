@file:Suppress("ConstPropertyName")

import org.gradle.api.*
import java.io.*

fun Project.packageName(): String =
  when (path) {
    ":core" -> rootProject.name
    else -> "${rootProject.name}${path.replace(":", "-")}"
  }

object Config {
  var rootDir: File? = null
  var githubMirror: String = ""
  var buildNative: Boolean = false
  val properties by lazy {
    java.util.Properties().apply {
      File(rootDir!!, "gradle.properties").inputStream().use {
        load(it)
      }
    }
  }

  fun get(property: String): String =
    properties.getProperty(property) ?: error("$property not found in gradle.properties")
}

object Versions {
  const val autoServiceAnno = "1.1.1"
  const val autoServiceKsp = "1.2.0"
  const val kotlinpoet = "2.3.0"
  const val ksp = "2.3.10"
  val kotlin by lazy { Config.get("kotlinVersion") }
  const val mindustry = "v160.4"
  const val lwjgl = "3.3.6"
  const val antlr = "1.0.14"
  const val jline = "3.30.17"
}

object Library {
  const val kspApi = "com.google.devtools.ksp:symbol-processing-api:${Versions.ksp}"
  const val autoServiceAnno =
    "com.google.auto.service:auto-service-annotations:${Versions.autoServiceAnno}"
  const val autoServiceKsp =
    "dev.zacsweers.autoservice:auto-service-ksp:${Versions.autoServiceKsp}"
  const val kotlinpoet = "com.squareup:kotlinpoet:${Versions.kotlinpoet}"
  const val javapoet = "com.palantir.javapoet:javapoet:0.22.0"
  const val kotlinpoetKsp = "com.squareup:kotlinpoet-ksp:${Versions.kotlinpoet}"
  val compiler = "org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}"
  val gradleApi = "org.jetbrains.kotlin:kotlin-gradle-plugin-api:${Versions.kotlin}"
  const val mindustry = "Anuken:Mindustry:${Versions.mindustry}"
  const val lwjgl = "org.lwjgl:lwjgl:${Versions.lwjgl}"
  const val lwjglCore = "org.lwjgl:lwjgl"
  const val lwjglBom = "org.lwjgl:lwjgl-bom:${Versions.lwjgl}"
  fun lwjglModule(name: String) = "org.lwjgl:lwjgl-$name"
  fun lwjglVersion(name: String) = "org.lwjgl:lwjgl-$name:${Versions.lwjgl}"

}
