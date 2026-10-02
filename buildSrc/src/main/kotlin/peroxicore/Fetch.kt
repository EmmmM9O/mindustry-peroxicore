package peroxicore

import org.gradle.api.*
import org.gradle.api.file.*
import java.io.*
import java.net.*

fun Project.fetchGithub(
  owner: String,
  repo: String,
  tag: String,
  assetName: String,
  targetDir: File = layout.buildDirectory
    .dir("github-release/$repo-$tag")
    .get().asFile,
): ConfigurableFileCollection {
  val file = targetDir.resolve(assetName)

  if (file.exists() && file.length() > 0) {
    return files(file.absolutePath)
  }

  targetDir.mkdirs()
  val url = URI(
    "https://github.com/$owner/$repo/releases/download/$tag/$assetName"
  ).toURL()
  val conn = (url.openConnection() as HttpURLConnection).apply {
    instanceFollowRedirects = true
    connectTimeout = 15_000
    readTimeout = 60_000
    requestMethod = "GET"
    setRequestProperty("Accept", "application/octet-stream")
    setRequestProperty("User-Agent", "gradle-github-release-downloader")
  }

  try {
    val code = conn.responseCode
    if (code !in 200..299) {
      throw RuntimeException(
        "Download failed: $url -> HTTP $code ${conn.responseMessage}"
      )
    }
    val tmp = File(targetDir, "$assetName.part")
    conn.inputStream.use { input ->
      tmp.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    if (file.exists()) file.delete()
    tmp.renameTo(file)
  } finally {
    conn.disconnect()
  }

  return files(file.absolutePath)
}