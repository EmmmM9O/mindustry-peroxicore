import com.github.jengelman.gradle.plugins.shadow.tasks.*
import java.util.*
import org.jetbrains.kotlin.gradle.tasks.*

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
  return project.findProperty(key) as? String ?: System.getenv(key)
}

Config.githubMirror = getProperty("GITHUB_MIRROR") ?: ""

Config.buildNative = getProperty("BUILD_NATIVE") == "true"

plugins {
  `java-library`
  `maven-publish`
  java
  idea
  kotlin("jvm")
  id("com.google.devtools.ksp") version Versions.ksp
  id("com.strumenta.antlr-kotlin") version Versions.antlr
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
    withSourcesJar()
  }

  kotlin {
    jvmToolchain(25)
    sourceSets {
      getByName("main") {
        kotlin.srcDirs("src", "build/generated/kotlin")
        kotlin.srcDirs("build/generated/ksp/main/kotlin")
      }
      getByName("test") {
        kotlin.srcDirs("test")
      }
    }
  }

  sourceSets {
    main {
      java.srcDirs("src", "build/generated/java", "build/generated/ksp/main/java")
    }
  }

  ksp {
    arg("mod_version", modVersion)
    arg("mod_subtitle", "Radical")
    arg("mod_author", "Stellarcus")
    arg("min_game_version", Versions.mindustry.substring(1))
    arg("mod_repo", "https://github.com/EmmmM9O/mindustry-peroxicore")
    arg("mod_hidden", "true")
  }

  idea {
    module {
      //      sourceDirs = sourceDirs + file("build/generated/ksp/main/kotlin")
      generatedSourceDirs =
        generatedSourceDirs +
          file("build/generated/ksp/main/kotlin") +
          file("build/generated/ksp/main/java") +
          file("build/generated/ksp/test/kotlin") +
          file("build/generated/ksp/test/java")
    }
  }
  repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
    ivy {
      name = "MindustryDeps"
      url = uri("${Config.githubMirror}https://github.com/Anuken/")
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

    testImplementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
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
      if (project.path != ":core") {
        dependencies {
          exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib.*"))
          exclude(dependency("org.jetbrains.kotlin:kotlin-reflect.*"))
        }
      }
      archiveFileName.set("${project.packageName()}-origin.jar")
      from("assets/") { include("**") }
    }

    val pkgName = project.packageName()
    val shadowJarTask = named<Jar>("shadowJar")

    val dexJar = layout.buildDirectory.file("libs/$pkgName-dex.jar")
    val originJar = shadowJarTask.flatMap { it.archiveFile }

    val d8Compile =
      register<JavaExec>("d8Compile") {
        description = "Compile Dex"
        group = "deployment"

        inputs.files(configurations.compileClasspath)
        inputs.file(originJar)
        outputs.file(dexJar)

        mainClass.set("com.android.tools.r8.D8")
        workingDir = layout.buildDirectory.dir("libs").get().asFile
        classpath = files()

        doFirst {
          val r8Jar =
            configurations.compileClasspath.get().files.find {
              it.name.startsWith("r8-") && it.extension == "jar"
            } ?: throw GradleException("No R8.jar")
          classpath = files(r8Jar)

          val argsList = mutableListOf<String>()
          configurations.compileClasspath.get().files.forEach { f ->
            if (f.isFile && f.extension == "jar" && !f.name.startsWith("r8-")) {
              argsList += listOf("--classpath", f.absolutePath)
            }
          }
          argsList +=
            listOf(
              "--min-api",
              "26",
              "--output",
              dexJar.get().asFile.absolutePath,
              originJar.get().asFile.absolutePath,
            )
          args = argsList
        }
      }

    val deploy =
      register<Jar>("deploy") {
        description = "Build Mod"
        group = "deployment"
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveFileName.set("$pkgName.jar")

        from(zipTree(originJar))

        from(zipTree(dexJar))
        dependsOn(d8Compile)

        inputs.files(originJar, dexJar)
      }
  }
  group = "com.github.emmmM9O"
  version = "main-SNAPSHOT"
  publishing {
    publications {
      create<MavenPublication>("library") {
        from(components["java"])
        groupId = "com.github.emmmM9O.mindustry-peroxicore"
        artifactId = project.path.substring(1).replace(":", "-")
        version = "main-SNAPSHOT"
      }
    }
  }
}

tasks.register<Copy>("deployAll") {
  description = "Deploy all mods"
  group = "deployment"

  into(layout.buildDirectory.dir("deploy"))
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
  val targetProjects = listOf(":core", ":ponder")
  var deployTasks = targetProjects.map { "$it:deploy" }
  dependsOn(deployTasks)
  deployTasks.forEach { mustRunAfter(it) }
  targetProjects.forEach {
    val sub = project(it)
    from("${sub.layout.buildDirectory.get()}/libs/${sub.packageName()}.jar")
  }
  val platformProjects = listOf(":lwjgl", ":lwjgl:ktx", ":lwjgl:font")
  deployTasks = platformProjects.map { "$it:deploy" }
  dependsOn(deployTasks)
  deployTasks.forEach { mustRunAfter(it) }
  platformProjects.forEach {
    val sub = project(it)
    from("${sub.layout.buildDirectory.get()}/libs/${sub.packageName()}-android.jar")
    from("${sub.layout.buildDirectory.get()}/libs/${sub.packageName()}-desktop.jar")
  }
}
