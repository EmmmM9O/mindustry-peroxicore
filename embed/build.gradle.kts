dependencies {
  compileOnly(project(":core"))
  compileOnly(project(":ponder"))
  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
  implementation("com.github.EB-wilson.UniverseKit:reflection:1.4")
}

val embedJars = tasks.register<Copy>("embedJars") {
  val embeds = arrayOf(":core", ":ponder")
  embeds.forEach {
    from(project(it).tasks.named<Jar>("deploy").flatMap { it.archiveFile })
  }
  into(layout.buildDirectory.dir("generated/resources"))
}

tasks.named<ProcessResources>("processResources") {
  from(embedJars) {
    into("META-INF/jarjar")
  }
}
