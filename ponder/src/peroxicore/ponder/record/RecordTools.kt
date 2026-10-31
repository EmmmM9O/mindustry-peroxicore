package peroxicore.ponder.record

import arc.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import peroxicore.*
import peroxicore.ponder.*
import peroxicore.ponder.scene.*
import peroxicore.ui.*

class RecordTools : FloatingTable() {
  val defSize = PeroxiCore.debugTools.defSize

  init {
    key = "peroxicore-record-tools"
    val style = Styles.cleari
    PeroxiCore.debugTools.buttonsBar
      .button(Icon.pick, style) {
        this.visible = !this.visible
      }
      .size(defSize)

    reset()
    add(dragButton).size(defSize)

    button(Icon.settings, style) {
        if (PonderRecorder.active) PonderRecorder.stop()
        else {
          Core.app.post { PonderRecorder.start() }
        }
      }
      .size(defSize)
    button(Icon.play, style) {
        if (!PonderRecorder.active) PonderRecorder.preview()
      }
      .size(defSize)
    button(Icon.copy, style) {
        if (PonderRecorder.hasRecording) {
          val text = PonderRecorder.exportKotlinDsl()
          Core.app.setClipboardText(text)
          FullTextDialog().show("思索 Kotlin DSL（已复制）", text)
        }
      }
      .size(defSize)
    button(Icon.save, style) {
        if (PonderRecorder.hasRecording) {
          val text = PonderRecorder.exportKotlinDsl()
          FileChooser.export("ponder-scene", "kt") { file -> file.writeString(text) }
        }
      }
      .size(defSize)
    Core.scene.add(this)
    this.visible = false
    loadPosition()
  }
}
