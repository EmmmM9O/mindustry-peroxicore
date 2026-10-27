package peroxicore

import arc.*
import arc.assets.*
import arc.files.*
import arc.func.*
import arc.struct.*
import arc.util.*
import arc.util.io.*
import mindustry.*
import mindustry.Vars.*
import mindustry.game.EventType.*
import mindustry.mod.*
import mindustry.mod.Mods.*
import universe.util.reflect.*

import peroxicore.mod.*

object PeroxiCoreEmbed {
  val metaFiles = arrayOf("mod.json", "mod.hjson", "plugin.json", "plugin.hjson")
  val loadedMods = HashSet<String>()
  val loadMod: Mods.(Fi, Boolean, Boolean) -> LoadedMod = accessMethod3("loadMod")
  val mapping = ObjectMap<String, Fi>()
  val loadList = arrayOf("peroxicore-embed", "peroxicore-ponder")

  val embedDir = dataDirectory.child("peroxicore_embed/")

  fun setup() {
    Vars.platform = POEmbedPlatform(Vars.platform, this.javaClass.classLoader)
    checkModList()
    embedDir.mkdirs()
    loadList.forEach { mod ->
      val stream =
        javaClass.classLoader.getResourceAsStream("META-INF/jarjar/$mod.jar")
          ?: run {
            Log.warn("PeroxiCoreEmbed cannot find jarinjar $mod")
            return@forEach
          }
      val jar = embedDir.child("$mod.jar")
      if (jar.exists()) jar.delete()
      stream.use { input ->
        jar.file().outputStream().use { output ->
          input.copyTo(output)
        }
      }
      loadMod(jar)
    }
  }

  fun loadMod(file: Fi) {
    val meta =
      runCatching {
        mods.findMeta(resolveRoot(if (file.isDirectory) file else ZipFi(file)))
      }
        .getOrNull()
        ?.takeIf { it.name != null }
        ?: run {
          Log.warn("PeroxicoreEmbed cannot load Mod ${file}")
          return
        }

    if (meta.internalName in loadedMods) {
      Log.info("PeroxiCoreEmbed Skip ${file} due to already loaded")
      return
    }

    try {
      val mod = mods.loadMod(file, false, true)
      mod.state = ModState.enabled
      mods.mods.add(mod)
    } catch (e: Throwable) {
      if (e is ClassNotFoundException && e.message.orEmpty().contains("mindustry.plugin.Plugin")) {
        Log.warn(
          "Plugin '@' is outdated and needs to be ported to v7! Update its main class to inherit from 'mindustry.mod.Plugin'.",
          file.name(),
        )
      } else if (steam) {
        Log.err("Failed to load mod workshop file @. Skipping.", file)
        Log.err(e)
      } else {
        Log.err("Failed to load mod file @. Skipping.", file)
        Log.err(e)
      }
    }
  }

  fun checkModList() {
    val candidates = Seq<Fi>()

    Seq.with(*modDirectory.list())
      .retainAll { f ->
        f.extEquals("jar") ||
          f.extEquals("zip") ||
          (f.isDirectory &&
            Structs.contains(metaFiles) { meta ->
              resolveRoot(f).child(meta).exists()
            })
      }
      .each { candidates.add(it) }

    val metas = Seq<ModMeta>()

    for (file in candidates) {
      val meta =
        runCatching {
          mods.findMeta(resolveRoot(if (file.isDirectory) file else ZipFi(file)))
        }
          .getOrNull()
          ?.takeIf { it.name != null } ?: continue
      metas.add(meta)
      mapping.put(meta.internalName, file)
    }

    val resolved = mods.resolveDependencies(metas)
    for (entry in resolved) {
      if (entry.value == ModState.enabled) loadedMods.add(entry.key)
    }
  }

  fun resolveRoot(fi: Fi): Fi {
    if (OS.isMac && fi !is ZipFi) fi.child(".DS_Store").delete()
    val files = fi.list()
    return if (files.size == 1 && files[0].isDirectory) files[0] else fi
  }
}
