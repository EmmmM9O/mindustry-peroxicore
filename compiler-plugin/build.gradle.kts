dependencies {
  compileOnly(Library.compiler)
  compileOnly(Library.autoServiceAnno)
  ksp(Library.autoServiceKsp)
}

publishing {
  publications {
    named<MavenPublication>("library") {
      version = "${Versions.kotlin}-0.1.0"
    }
  }
}
