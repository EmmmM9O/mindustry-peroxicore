package peroxicore.mod

import arc.files.*
import arc.util.*
import arc.util.serialization.*
import arc.util.serialization.Jval.*

object PXCMods {
  val json = Json()
  val metaFiles = arrayOf("peroxicore.json")

  fun resolveRoot(fi: Fi): Fi {
    if (OS.isMac && fi !is ZipFi) fi.child(".DS_Store").delete()
    val files = fi.list()
    return if (files.size == 1 && files[0].isDirectory()) files[0] else fi
  }

  fun findMeta(file: Fi): PeroxicoreMeta? {
    var metaFile: Fi? = null
    for (name in metaFiles) {
      metaFile = file.child(name)
      if (metaFile.exists()) break
    }

    if (metaFile == null || !metaFile.exists()) {
      return null
    }

    val meta =
      json
        .fromJson(
          PeroxicoreMeta::class.java,
          Jval.read(metaFile.readString()).toString(Jformat.plain)
        )

    return meta
  }

  fun load(sourceFile: Fi): PeroxicoreMeta? {
    val zip = resolveRoot(if (sourceFile.isDirectory()) sourceFile else ZipFi(sourceFile))

    val meta = findMeta(zip)

    if (meta != null) {
      Log.info("Find peroxicore mod: ${sourceFile.name()}")
    }

    return meta
  }

  class PeroxicoreMeta {
    val peroxicoreLoader = true
  }
}
