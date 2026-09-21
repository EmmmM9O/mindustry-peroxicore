import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.*
import java.util.*
import javax.management.*

Config.rootDir = project.rootDir

fun getProperty(key: String): String? {
  val localPropertiesFile = rootProject.file("local.properties")
  if (localPropertiesFile.exists()) {
    val localProperties =
      Properties().apply {
        load(localPropertiesFile.inputStream())
      }
    localProperties.getProperty(key)?.let {
      return it
    }
  }
  return project.findProperty(key) as? String
}

plugins {
  `java-library`
  `maven-publish`
  java
  idea
  kotlin("jvm")
  id("com.google.devtools.ksp") version Versions.ksp
  id("com.gradleup.shadow") version "9.3.0"
}

val modVersion = getProperty("modVersion") ?: System.getenv("MOD_VERSION") ?: "0"

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "idea")
  apply(plugin = "com.google.devtools.ksp")
  apply(plugin = "com.gradleup.shadow")

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(25))
    }
  }

  kotlin {
    jvmToolchain(25)
    sourceSets {
      getByName("main") {
        kotlin.srcDirs("src")
        kotlin.srcDirs("build/generated/ksp/main/kotlin")
      }
    }
  }

  ksp {
    arg("mod_version", modVersion)
    arg("mod_subtitle", "Radical")
    arg("mod_author", "Stellarcus")
    arg("min_game_version", Versions.mindustry.substring(1))
    arg("mod_repo", "https://github.com/EmmmM9O/mindustry-peroxicore")
  }

  idea {
    module {
      //      sourceDirs = sourceDirs + file("build/generated/ksp/main/kotlin")
      generatedSourceDirs =
        generatedSourceDirs +
        file("build/generated/ksp/main/kotlin") +
        file("build/generated/ksp/test/kotlin")
    }
  }
  repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
    ivy {
      name = "MindustryDeps"
      url = uri("https://gh-proxy.org/github.com/Anuken/")
      patternLayout {
        artifact("/Mindustry/releases/download/[revision]/dependencies.[ext]")
      }
      metadataSources { artifact() }
    }
    google()
  }
  dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly("com.android.tools:r8:9.1.31")
  }
  tasks {
    test {
      useJUnitPlatform()
      testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
      }
      minHeapSize = "128m"
      maxHeapSize = "512m"
      filter {
        includeTestsMatching("*Test")
      }
    }

    withType<KotlinCompile> {
      compilerOptions {
        freeCompilerArgs.set(listOf("-XXLanguage:+NestedTypeAliases"))
      }
    }
    withType<JavaCompile>().configureEach {
      sourceCompatibility = "25"
      targetCompatibility = "25"
      options.encoding = "UTF-8"
    }

    named<Jar>("jar") {
      exclude("mod.json")
    }

    withType<ShadowJar> {
      archiveFileName.set("${project.packageName()}-desktop.jar")
      from("assets/") { include("**") }
    }

    register<JavaExec>("d8Compile") {
      dependsOn("shadowJar")

      doFirst {
        val r8Jar =
          configurations.compileClasspath.get().files.find {
            it.name.startsWith("r8-") && it.extension == "jar"
          } ?: throw GradleException("No R8.jar")

        classpath(files(r8Jar))
        mainClass.set("com.android.tools.r8.D8")

        val argsList = mutableListOf<String>()
        // argsList.add("--lib")
        // argsList.add(File(platformRoot, "android.jar").absolutePath)
        configurations.compileClasspath.get().files.forEach { file ->
          if (
            file.isFile &&
            file.extension == "jar" &&
            !file.name.startsWith("r8-") // && file.name.startsWith("Mindustry")
          ) {
            argsList.add("--classpath")
            argsList.add(file.absolutePath)
          }
        }

        argsList.add("--min-api")
        argsList.add("26")
        argsList.add("--output")
        argsList.add("${project.packageName()}-android.jar")
        argsList.add("${project.packageName()}-desktop.jar")

        args = argsList
        workingDir =
          layout.buildDirectory
            .dir("libs")
            .get()
            .asFile
      }
    }
    register<Jar>("deploy") {
      group = "deployment"
      dependsOn("d8Compile")
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("${project.packageName()}.jar")
      from(
        zipTree("${layout.buildDirectory.get()}/libs/${project.packageName()}-desktop.jar"),
        zipTree("${layout.buildDirectory.get()}/libs/${project.packageName()}-android.jar")
      )
    }
  }
  group = "com.github.emmmm9o"
  version = modVersion
  publishing {
    publications {
      create<MavenPublication>("library") {
        from(components["java"])
        groupId = "com.github.emmmm9o"
        artifactId = project.packageName()
        version = modVersion
      }
    }
  }
}

tasks.register<Copy>("deployAll") {
  group = "deployment"

  into(layout.buildDirectory.dir("deploy"))
  duplicatesStrategy = DuplicatesStrategy.INCLUDE

  val targetProjects = listOf(":core", ":ponder")

  val deployTasks = targetProjects.map { "${it}:deploy" }
  dependsOn(deployTasks)
  deployTasks.forEach { mustRunAfter(it) }
  targetProjects.forEach {
    val sub = project(it)
    from("${sub.layout.buildDirectory.get()}/libs/${sub.packageName()}.jar")
  }
}
