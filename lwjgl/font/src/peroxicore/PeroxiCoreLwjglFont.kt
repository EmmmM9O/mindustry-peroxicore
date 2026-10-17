package peroxicore

import arc.*
import arc.freetype.*
import arc.scene.ui.*
import arc.util.*
import mindustry.*
import mindustry.mod.*
import mindustry.ui.dialogs.*
import org.lwjgl.system.*
import peroxicore.annotations.*

@ModConfig(
  name = "peroxicore-lwjgl-font",
  displayName = "PeroxiCore Lwjgl Font",
  dependencies = ["peroxicore", "peroxicore-lwjgl"],
)
@ImportPeroxiCore
class PeroxiCoreLwjglFont : Mod() {
  init {
    Configuration.HARFBUZZ_LIBRARY_NAME.set("freetype")
  }

  override fun init() {
    /*
    val file = Vars.modDirectory.child("test.ttf")
    Time.run(1f) {
      val fontG = FreeTypeFontGenerator(file)
      val font = fontG.generateFont(FreeTypeFontGenerator.FreeTypeFontParameter())

      val pfont = PFreeTypeFont.load(file.file().absolutePath, 16)

      BaseDialog("Label Test").apply{
        val testCont = "!= >= <= === -> <- ++ |---| ---      ~/projects/peroxicore   on    main !18 ?2 ▓▒░ 这是中文"
        cont.add("ArcLabel").row()
        cont.add(Label(testCont, Label.LabelStyle().apply{
          this.font = font
        })).row()
        cont.add("PeroxiCoreLabel").row()
        cont.add(PLabel(pfont, testCont))

        addCloseButton()
      }.show()
    }*/
  }
}
