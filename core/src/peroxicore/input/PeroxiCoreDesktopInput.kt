package peroxicore.input

import arc.*
import arc.Core.*
import arc.input.*
import mindustry.*
import mindustry.Vars.*
import mindustry.core.*
import mindustry.input.*

class PeroxiCoreDesktopInput : PeroxiCoreInput() {
  val mdtInput = Vars.control.input as DesktopInput

  /** Position where the player started dragging the selection box. */
  var selectX = 0
  var selectY = 0
  var lastX = 0
  var lastY = 0

  override fun update() {
    super.update()

    if (mode == InputMode.selecting) {
      if (state.isMenu()) {
        cancelSelection()
        return
      }

      val world = Core.input.mouseWorld()
      lastX = World.toTile(world.x)
      lastY = World.toTile(world.y)
      autoPan()

      if (Core.input.keyRelease(Binding.select)) {
        mode = InputMode.none
        invokeSelectTask(selectX, selectY, lastX, lastY)
      } else if (Core.input.keyTap(Binding.deselect) || Core.input.keyTap(KeyCode.escape)) {
        cancelSelection()
      }
      return
    }

    if (state.isMenu() || !isSelectionAvailable() || Core.scene.hasMouse() || Core.scene.hasKeyboard()) return

    if (Core.input.keyTap(Binding.select)) {
      val world = Core.input.mouseWorld()
      mode = InputMode.selecting
      selectX = World.toTile(world.x)
      selectY = World.toTile(world.y)
      lastX = selectX
      lastY = selectY
    }
  }

  private fun cancelSelection() {
    mode = InputMode.none
    selectionTask.func = null
  }

  override fun drawTop() {
    super.drawTop()
    if (mode == InputMode.selecting) {
      drawSelection(
        selectX,
        selectY,
        lastX,
        lastY,
        selectionTask.col1,
        selectionTask.col2,
        selectionTask.text,
        selectionTask.maxLength,
      )
    }
  }
}
