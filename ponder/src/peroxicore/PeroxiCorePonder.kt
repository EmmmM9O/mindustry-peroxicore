package peroxicore

import arc.*
import mindustry.*
import mindustry.game.*
import mindustry.mod.*
import peroxicore.annotations.*
import peroxicore.ponder.*
import peroxicore.ponder.record.*

@ModConfig(
  name = "peroxicore-ponder",
  displayName = "PeroxiCore Ponder",
  dependencies = ["peroxicore"]
)
@ImportPeroxiCore
class PeroxiCorePonder : Mod() {
  companion object {
    lateinit var recordTools: RecordTools
  }
  init {
    if (!Vars.clientLoaded) {
      if (!Vars.headless) {
        PonderCore.setup()
        val core = (Core.app.listeners.find { it is ApplicationCore } as ApplicationCore)
        core.add(PonderCore)
      }
      Events.on(EventType.ClientLoadEvent::class.java) {
        if (Vars.headless) return@on
        PonderCore.initPonder()
        recordTools = RecordTools()
      }
    }
  }

  override fun init() {
  }

  override fun loadContent() {}
}
