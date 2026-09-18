package peroxicore.mod

import arc.*
import arc.files.*
import arc.math.*
import arc.struct.*
import arc.util.serialization.*
import mindustry.*
import mindustry.core.*
import mindustry.mod.*
import mindustry.net.*
import mindustry.net.Net.*
import mindustry.type.*
import mindustry.ui.FileChooser.*
import java.net.*

class PXCPlatform(
  val platform: Platform,
  val core: ClassLoader,
) : Platform by platform {
  val coreLoader = PXCModClassLoader(core)

  @Throws(Exception::class)
  override fun loadJar(
    jar: Fi,
    parent: ClassLoader,
  ): ClassLoader =
    PXCMods.load(jar)?.takeIf { it.peroxicoreLoader }?.let {
      platform.loadJar(jar, coreLoader)
    } ?: platform.loadJar(jar, parent)

  override fun updateLobby() = platform.updateLobby()

  override fun inviteFriends() = platform.inviteFriends()

  override fun publish(pub: Publishable) = platform.publish(pub)

  override fun viewListing(pub: Publishable) = platform.viewListing(pub)

  override fun viewListingID(mapid: String) = platform.viewListingID(mapid)

  override fun getWorkshopContent(type: Class<out Publishable>): Seq<Fi> =
    platform.getWorkshopContent(type)

  override fun openWorkshop() = platform.openWorkshop()

  override fun getNet() = platform.getNet()

  override fun createScripts() = platform.createScripts()

  override fun getScriptContext() = platform.getScriptContext()

  override fun updateRPC() = platform.updateRPC()

  override fun getUUID(): String = platform.getUUID()

  override fun shareFile(file: Fi) = platform.shareFile(file)

  override fun showFileChooser(params: FileChooserParams) = platform.showFileChooser(params)

  override fun hide() = platform.hide()

  override fun beginForceLandscape() = platform.beginForceLandscape()

  override fun endForceLandscape() = platform.endForceLandscape()
}
