package peroxicore

import arc.*
import mindustry.*
import mindustry.game.*
import mindustry.mod.*
import peroxicore.annotations.*
import peroxicore.ponder.*

@ModConfig(
  name = "peroxicore-ponder",
  displayName = "PeroxiCore Ponder",
  dependencies = ["peroxicore"]
)
@ImportPeroxiCore
class PeroxiCorePonder : Mod() {
  init {
    if (!Vars.clientLoaded) {
      if (!Vars.headless) {
        PonderCore.setup()
        val core = (Core.app.listeners.find { it is ApplicationCore } as ApplicationCore)
        core.add(PonderCore)
      }
      Events.on(EventType.ClientLoadEvent::class.java) {
        PonderCore.initPonder()
      }
    }
  }

  override fun init() {
  }

  override fun loadContent() {}
}
