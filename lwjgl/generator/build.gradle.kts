kotlin {
  sourceSets {
    getByName("main") {
      kotlin.srcDir("templates")
    }
  }
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
}

tasks.register<JavaExec>("generate") {
  dependsOn(tasks.named("classes"), configurations.runtimeClasspath)
  mainClass.set("peroxicore.BuildGeneratorKt")
  classpath = sourceSets["main"].runtimeClasspath
  workingDir = project.rootDir
  args(project.projectDir.parentFile.absolutePath)
}
