import peroxicore.*
import java.net.*
import java.util.zip.*

val hiddenApiBypassUrl =
  "https://repo1.maven.org/maven2/org/lsposed/hiddenapibypass/hiddenapibypass/6.1/hiddenapibypass-6.1.aar"
val aarDir =
  layout.buildDirectory
    .dir("third-party/aar")
    .get()
    .asFile
val jarDir =
  layout.buildDirectory
    .dir("third-party/jar")
    .get()
    .asFile
val aarFile = aarDir.resolve("hiddenapibypass-6.2.aar")
val extractedJar = jarDir.resolve("hiddenapibypass.jar")

aarDir.mkdirs()
jarDir.mkdirs()

if (!extractedJar.exists()) {
  if (!aarFile.exists()) {
    println("Downloading AAR: $hiddenApiBypassUrl")
    URI(hiddenApiBypassUrl).toURL().openStream().use { input ->
      aarFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
  }

  ZipFile(aarFile).use { zip ->
    val entry =
      zip.getEntry("classes.jar")
        ?: throw GradleException("AAR not find classes.jar")
    zip.getInputStream(entry).use { input ->
      extractedJar.outputStream().use { output ->
        input.copyTo(output)
      }
    }
  }
  println("Get: $extractedJar")
}
val lwjglCore = androidLwjgl("lwjgl")
val lwjglNative = androidLwjgl("lwjgl-natives")

dependencies {
  compileOnlyApi(project(":core"))
  ksp(project(":ksp"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))

  implementation(Library.lwjglVersion("opengles"))

  implementation(files(extractedJar))
  implementation(lwjglCore)
  implementation(lwjglNative)
}
