dependencies {
  compileOnly(project(":core"))
  compileOnly(project(":ponder"))
  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
  implementation("com.github.EB-wilson.UniverseKit:reflection:1.4")
}

val embedJars = tasks.register<Copy>("embedJars") {
  from(project(":core").tasks.named<Jar>("deployEmbed").flatMap { it.archiveFile })
  from(project(":ponder").tasks.named<Jar>("deploy").flatMap { it.archiveFile })
  into(layout.buildDirectory.dir("generated/resources"))
}

tasks.named<ProcessResources>("processResources") {
  from(embedJars) {
    into("META-INF/jarjar")
  }
}
