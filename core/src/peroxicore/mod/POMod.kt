package peroxicore.mod

import mindustry.*
import mindustry.mod.*

open class POMod : Mod(){
  val modState: Mods.LoadedMod by lazy { Vars.mods.getMod(this.javaClass) }
}
