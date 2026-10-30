import com.github.jengelman.gradle.plugins.shadow.tasks.*
import com.strumenta.antlrkotlin.gradle.*

plugins {
  id("com.strumenta.antlr-kotlin")
}

dependencies {
  compileOnlyApi(Library.mindustry)
  api(project(":annotations"))
  api(kotlin("stdlib-jdk8"))
  api(kotlin("reflect"))
  api("com.github.EB-wilson.UniverseKit:reflection:1.4")
  api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
  compileOnlyApi("com.github.EB-wilson.UniverseKit:platform:1.4")
  compileOnlyApi("com.github.EB-wilson.UniverseKit:expects:1.4")

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  implementation("com.strumenta:antlr-kotlin-runtime:${Versions.antlr}")
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
  dependsOn("cleanGenerateKotlinGrammarSource")

  source = fileTree(layout.projectDirectory.dir("antlr")) {
    include("**/*.g4")
  }

  val pkgName = "peroxicore.parsers"
  packageName = pkgName

  arguments = listOf("-visitor")

  val outDir = "generated/antlr/${pkgName.replace(".", "/")}"
  outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

kotlin {
  sourceSets {
    getByName("main") {
      kotlin.srcDir(generateKotlinGrammarSource)
    }
  }
}

tasks {
  val mainOutput = the<SourceSetContainer>()
    .named("main")
    .map { it.output }

  val shadowJarTask =
    register<ShadowJar>("shadowJarEmbed") {
      dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect.*"))
        exclude(dependency("com.github.EB-wilson.UniverseKit:.*:.*"))
      }
      archiveFileName.set("${project.packageName()}-originEmbed.jar")
      from("assets/") { include("**") }
      from(mainOutput)
      configurations = listOf(project.configurations.runtimeClasspath.get())
    }
  val pkgName = project.packageName()

  val dexJar = layout.buildDirectory.file("libs/$pkgName-dex-embed.jar")
  val originJar = shadowJarTask.flatMap { it.archiveFile }

  val d8Compile =
    register<JavaExec>("d8CompileEmbed") {
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
    register<Jar>("deployEmbed") {
      description = "Build Mod"
      group = "deployment"
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("$pkgName-embed.jar")

      from(zipTree(originJar))

      from(zipTree(dexJar))
      dependsOn(d8Compile)

      inputs.files(originJar, dexJar)
    }

}
