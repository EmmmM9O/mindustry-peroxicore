@file:Suppress("ConstPropertyName")

import org.gradle.api.*
import java.io.*

fun Project.packageName(): String = "${rootProject.name}${path.replace(":", "-")}"

object Config {
  var rootDir: File? = null
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
}

object Library {
  const val kspApi = "com.google.devtools.ksp:symbol-processing-api:${Versions.ksp}"
  const val autoServiceAnno =
    "com.google.auto.service:auto-service-annotations:${Versions.autoServiceAnno}"
  const val autoServiceKsp = "dev.zacsweers.autoservice:auto-service-ksp:${Versions.autoServiceKsp}"
  const val kotlinpoet = "com.squareup:kotlinpoet:${Versions.kotlinpoet}"
  const val kotlinpoetKsp = "com.squareup:kotlinpoet-ksp:${Versions.kotlinpoet}"
  val compiler = "org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}"
  const val mindustry = "Anuken:Mindustry:${Versions.mindustry}"
}
