package peroxicore

import mindustry.mod.*
import peroxicore.annotations.*

@ModConfig(
  name = "peroxicore-lwjgl-ktx",
  displayName = "PeroxiCore Lwjgl KTX",
  dependencies = ["peroxicore", "peroxicore-lwjgl"],
)
@ImportPeroxiCore
class PeroxiCoreLwjglKTX: Mod() {
}
