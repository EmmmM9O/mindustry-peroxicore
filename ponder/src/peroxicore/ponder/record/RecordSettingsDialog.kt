package peroxicore.ponder.record

import arc.graphics.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.ui.*
import mindustry.ui.dialogs.*

/**
 * 录制设置面板。
 *
 * 允许调整每个关键帧的时刻、删除关键帧与清空录制；修改会立即写回 [PonderRecorder.keyframes]。
 */
class RecordSettingsDialog : BaseDialog("Ponder 录制设置") {
  private val list = Table()

  init {
    shouldPause = true
    cont.clearChildren()
    cont.margin(12f)
    cont.add("调整关键帧时刻，或删除不需要的关键帧。").left().padBottom(8f).row()
    cont.pane { pane ->
        pane.top().left()
        pane.add(list).growX().top().left()
      }
      .grow()
    buttons.defaults().size(180f, 56f).pad(6f)
    buttons.button("清空录制", Icon.trash) {
      PonderRecorder.clear()
      rebuild()
    }
    addCloseButton()
    shown(this::rebuild)
  }

  fun rebuildIfShown() {
    if (isShown) rebuild()
  }

  private fun rebuild() {
    list.clearChildren()
    val recorder = PonderRecorder
    if (recorder.keyframes.isEmpty()) {
      list.add("暂无关键帧（先框选区域并开始录制）").color(Color.lightGray).pad(12f).row()
      return
    }
    recorder.keyframes.sortedBy { it.tick }.forEachIndexed { index, keyframe ->
      list.table { row ->
        row.left()
        row.add("关键帧 ${index + 1}（${keyframe.actions.size} 个改动）").left().growX()
        row.field(PonderRecorder.formatFloat(keyframe.tick), TextField.TextFieldFilter.floatsOnly) {
            value ->
            value.toFloatOrNull()?.let { keyframe.tick = it.coerceAtLeast(0f) }
          }
          .width(110f)
          .padLeft(8f)
        row.button(Icon.left, Styles.cleari) {
          keyframe.tick = (keyframe.tick - 5f).coerceAtLeast(0f)
          rebuild()
        }
        row.button(Icon.right, Styles.cleari) {
          keyframe.tick += 5f
          rebuild()
        }
        row.button(Icon.trash, Styles.cleari) {
          recorder.keyframes.remove(keyframe)
          rebuild()
        }
      }
      list.row()
    }
    list.add().height(10f).row()
    list.add("时间轴总长: ${PonderRecorder.duration.toInt()}t").color(Color.lightGray).left().row()
  }
}
