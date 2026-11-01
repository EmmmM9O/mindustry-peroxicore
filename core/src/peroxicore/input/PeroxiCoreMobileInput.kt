package peroxicore.input

import arc.*
import arc.Core.*
import arc.func.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.input.*
import arc.input.GestureDetector.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.scene.ui.layout.*
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
  val mdtInput = Vars.control.input as MobileInput
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
      autoPan()
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
        selectionTask.col1,
        selectionTask.col2,
        selectionTask.text,
        selectionTask.maxLength,
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
      return true
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
      invokeSelectTask(startX, startY, lastX, lastY)
      return true
    }
    return false
  }

  val vector = Vec2()
  val edgePan = Scl.scl(60f)
  val maxPanSpeed = 1.3f

  protected fun autoPan() {
    val screenX = Core.input.mouseX()
    val screenY = Core.input.mouseY()

    var panX = 0f
    var panY = 0f

    if (screenX <= edgePan) {
      panX = -(edgePan - screenX)
    }

    if (screenX >= Core.graphics.width - edgePan) {
      panX = (screenX - Core.graphics.width) + edgePan
    }

    if (screenY <= edgePan) {
      panY = -(edgePan - screenY)
    }

    if (screenY >= Core.graphics.height - edgePan) {
      panY = (screenY - Core.graphics.height) + edgePan
    }

    vector.set(panX, panY).scl(Core.camera.width / Core.graphics.width)
    vector.limit(maxPanSpeed)

    // pan view
    Core.camera.position.x += vector.x
    Core.camera.position.y += vector.y
  }
}
