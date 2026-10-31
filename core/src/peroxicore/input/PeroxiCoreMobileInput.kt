package peroxicore.input

import arc.*
import arc.Core.*
import arc.func.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.input.*
import arc.input.GestureDetector.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.pooling.*
import mindustry.*
import mindustry.Vars.*
import mindustry.content.*
import mindustry.core.*
import mindustry.entities.units.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.input.*
import mindustry.input.Placement.*
import mindustry.ui.*
import mindustry.world.*

class PeroxiCoreMobileInput : PeroxiCoreInput() {
  var startX = 0
  var startY = 0
  var lastX = 0
  var lastY = 0

  override fun update() {
    super.update()
    val world = Core.input.mouseWorld()
    val tileX = World.toTile(world.x)
    val tileY = World.toTile(world.y)
    if (mode == InputMode.selecting) {
      lastX = tileX
      lastY = tileY
    }
  }

  override fun drawTop() {
    super.drawTop()
    if (mode == InputMode.selecting) {
      drawSelection(
        startX,
        startY,
        lastX,
        lastY,
        selectionTask.maxLength,
        selectionTask.col1,
        selectionTask.col2,
        selectionTask.text,
      )
    }
  }

  override fun touchDown(
    screenX: Int,
    screenY: Int,
    pointer: Int,
    button: KeyCode,
  ): Boolean {
    if (state.isMenu()) return false

    val world = world(screenX, screenY)
    val tile = tile(world)

    if (tile == null || Core.scene.hasMouse(screenX.toFloat(), screenY.toFloat())) return false

    if (pointer == 0 && isSelectionAvailable()) {
      mode = InputMode.selecting
      startX = tile.x.toInt()
      startY = tile.y.toInt()
      lastX = startX
      lastY = startY
    }

    return false
  }

  override fun touchUp(
    screenX: Int,
    screenY: Int,
    pointer: Int,
    button: KeyCode,
  ): Boolean {
    if (mode == InputMode.selecting) {
      mode = InputMode.none
      selectionTask.func?.finish(startX, startY, lastX, lastY)
      selectionTask.func = null
    }
    return false
  }
}
