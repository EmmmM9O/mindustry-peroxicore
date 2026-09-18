dependencies {
  implementation(project(":annotations"))

  implementation(Library.kspApi)
  compileOnly(Library.autoServiceAnno)
  ksp(Library.autoServiceKsp)

  implementation(Library.kotlinpoet)
  implementation(Library.kotlinpoetKsp)

  implementation(Library.mindustry)
}
