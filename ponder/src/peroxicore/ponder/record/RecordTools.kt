package peroxicore.ponder.record

import arc.*
import arc.scene.style.*
import arc.scene.ui.*
import mindustry.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import peroxicore.*
import peroxicore.ui.*

/**
 * 录制工具栏。
 *
 * 按钮含义：
 * - record：开始框选 / 结束整个录制；
 * - stop：冻结（暂停）当前关键帧 / 提交并恢复游戏时间；
 * - preview：预览录制结果；
 * - export：选择格式导出；
 * - settings：关键帧时刻等设置。
 */
class RecordTools : FloatingTable() {
  val defSize = PeroxiCore.debugTools.defSize
  val style = Styles.cleari
  val settingsDialog = RecordSettingsDialog()

  val recordButton: ImageButton
  val stopButton: ImageButton
  val previewButton: ImageButton
  val exportButton: ImageButton

  init {
    key = "peroxicore-record-tools"
    PeroxiCore.debugTools.buttonsBar
      .button(Icon.pick, style) {
        this.visible = !this.visible
      }
      .size(defSize)

    reset()
    add(dragButton).size(defSize)

    recordButton = addButton(Icon.play) { onRecord() }
    stopButton = addButton(Icon.pause) { onStop() }
    previewButton = addButton(Icon.play) { onPreview() }
    exportButton = addButton(Icon.export) { onExport() }
    addButton(Icon.settings) { openSettings() }

    update {
      recordButton.getStyle().imageUp =
        when {
          !PonderRecorder.active -> Icon.play
          PonderRecorder.hasArea -> Icon.ok
          else -> Icon.cancel
        }
      stopButton.getStyle().imageUp = if (PonderRecorder.frozen) Icon.play else Icon.pause
      stopButton.isDisabled = !PonderRecorder.active
      previewButton.isDisabled = !PonderRecorder.hasRecording
      exportButton.isDisabled = !PonderRecorder.hasRecording
    }

    Core.scene.add(this)
    this.visible = false
    loadPosition()
  }

  private fun addButton(
    icon: Drawable,
    clicked: () -> Unit,
  ): ImageButton =
    button(icon, style) { clicked() }
      .size(defSize)
      .get()

  private fun onRecord() {
    val recorder = PonderRecorder
    if (!recorder.active) {
      Core.app.post { recorder.start() }
      return
    }
    // 还在框选阶段：取消会话。
    if (!recorder.hasArea) {
      recorder.clear()
      return
    }
    recorder.end()
    Vars.ui.showInfoFade("录制结束，共 ${recorder.keyframes.size} 个关键帧")
  }

  private fun onStop() {
    val recorder = PonderRecorder
    if (!recorder.active) return
    if (!recorder.hasArea) {
      Vars.ui.showInfoFade("请先框选录制区域")
      return
    }
    if (recorder.frozen) recorder.resume() else recorder.pause()
  }

  private fun onPreview() {
    if (!PonderRecorder.hasRecording) return
    Core.app.post { PonderRecorder.preview() }
  }

  private fun onExport() {
    if (!PonderRecorder.hasRecording) return
    if (PonderRecorder.exporters.isEmpty()) {
      Vars.ui.showInfoFade("没有可用的导出格式")
      return
    }
    ExportDialog().show()
  }

  private fun openSettings() {
    settingsDialog.rebuildIfShown()
    settingsDialog.show()
  }

  /** 导出格式选择对话框。 */
  private inner class ExportDialog : BaseDialog("导出录制") {
    init {
      shouldPause = true
      cont.clearChildren()
      cont.margin(12f)
      cont.add("选择导出格式：").left().padBottom(8f).row()
      PonderRecorder.exporters.forEach { exporter ->
        cont.button(exporter.name, Styles.flatt) {
          hide()
          doExport(exporter)
        }
        cont.row()
      }
      cont.button("复制到剪贴板（${PonderRecorder.exporters.first().name}）", Styles.flatt) {
        val text = PonderRecorder.exporters.first().build()
        Core.app.setClipboardText(text)
        hide()
        FullTextDialog().show("已复制导出内容", text)
      }
      cont.row()
      addCloseButton()
    }

    private fun doExport(exporter: SceneExporter) {
      val text = exporter.build()
      FileChooser.export("ponder-scene", exporter.extension) { file -> file.writeString(text) }
    }
  }
}
