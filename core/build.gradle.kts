import com.github.jengelman.gradle.plugins.shadow.tasks.*
import com.strumenta.antlrkotlin.gradle.*

plugins {
  id("com.strumenta.antlr-kotlin")
}

dependencies {
  compileOnlyApi(Library.mindustry)
  // 测试需要真实的 Arc / Mindustry 类（BoundingBox、Seq 等），它们只在运行时提供
  testImplementation(Library.mindustry)
  api(project(":annotations"))
  api(kotlin("stdlib-jdk8"))
  api(kotlin("reflect"))
  api("com.github.EB-wilson.UniverseKit:reflection:1.4")
  api("com.github.EB-wilson.UniverseKit:markdown:1.4")
  // markdown 的 Node 类型出现在 MarkdownProvider 的公开签名里，但 UK 只把它声明为
  // implementation，所以下游模块（例如 :core 的 PCodeBlockProvider）需要自己带上它。
  api("org.commonmark:commonmark:0.27.1")
  api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
  compileOnlyApi("com.github.EB-wilson.UniverseKit:platform:1.4")
  compileOnlyApi("com.github.EB-wilson.UniverseKit:expects:1.4")

  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  // CialloInterpreter is part of the public API (CialloNameResolver takes one), so the
  // ANTLR runtime types it extends must be on a consumer's compile classpath.
  api("com.strumenta:antlr-kotlin-runtime:${Versions.antlr}")
  implementation("org.jline:jline:${Versions.jline}")
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
  val d8 = configurations.getByName("d8")
  val mainOutput = the<SourceSetContainer>()
    .named("main")
    .map { it.output }

  val shadowJarTask =
    register<ShadowJar>("shadowJarEmbed") {
      dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect.*"))
        exclude(dependency("com.github.EB-wilson.UniverseKit:.*:.*"))
        exclude(dependency("org.jline:.*:.*"))
      }
      archiveFileName.set("${project.packageName()}-originEmbed.jar")
      // The console is a standalone-JVM feature.  JLine is intentionally
      // excluded from the Android embed artifact, so its classes go as well.
      exclude("peroxicore/ciallo/CialloConsole*.class")
      exclude("peroxicore/ciallo/CialloCompleter*.class")
      exclude("peroxicore/ciallo/CialloHighlighter*.class")
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

      inputs.files(d8)
      inputs.file(originJar)
      outputs.file(dexJar)

      mainClass.set("com.android.tools.r8.D8")
      workingDir = layout.buildDirectory.dir("libs").get().asFile
      classpath(d8)

      doFirst {
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

tasks.withType<Jar>().configureEach {
  if (name != "sourcesJar" && name != "javadocJar") {
    manifest {
      attributes["Main-Class"] = "peroxicore.Main"
    }
  }
}
