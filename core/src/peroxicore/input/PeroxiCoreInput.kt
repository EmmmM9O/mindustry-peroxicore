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
import universe.util.reflect.*

enum class InputMode {
  none,
  selecting,
}

fun interface SelectionTaskFunc {
  fun finish(x1: Int, y1: Int, x2: Int, y2: Int)
}

data class SelectionTask(
  var maxLength: Int = 0,
  var col1: Color = Pal.accentBack,
  var col2: Color = Pal.accent,
  var text: String? = null,
  var func: SelectionTaskFunc? = null,
)

abstract class PeroxiCoreInput() : InputProcessor, GestureListener {
  lateinit var detector: GestureDetector

  fun add() {
    detector = GestureDetector(20f, 0.5f, 0.3f, 0.15f, this)
    Core.input.inputMultiplexer.addProcessor(0, this)
    Core.input.inputMultiplexer.addProcessor(0, detector)
  }

  open fun update() {}

  open fun drawTop() {}

  protected fun drawSelection(x1: Int, y1: Int, x2: Int, y2: Int, maxLength: Int) {
    drawSelection(x1, y1, x2, y2, maxLength, Pal.accentBack, Pal.accent, null)
  }

  protected fun drawSelection(
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
    maxLength: Int,
    col1: Color,
    col2: Color,
    text: String?,
  ) {
    val result: NormalizeDrawResult =
      Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, maxLength, 1f)

    Lines.stroke(2f)

    Draw.color(col1)
    Lines.rect(result.x, result.y - 1, result.x2 - result.x, result.y2 - result.y)
    Draw.color(col2)
    Lines.rect(result.x, result.y, result.x2 - result.x, result.y2 - result.y)

    if (text != null) {
      val font = Fonts.outline
      font.setColor(col2)
      val ints = font.usesIntegerPositions()
      font.setUseIntegerPositions(false)
      val z = Draw.z()
      Draw.z(Layer.endPixeled)
      font.getData().setScale(1 / renderer.camerascale)

      val width = ((result.x2 - result.x) / tilesize).toInt()
      val height = ((result.y2 - result.y) / tilesize).toInt()
      val area = width * height
      val offset = 5f / renderer.camerascale * tilesize

      font.draw(
        "$text: $width" + "x" + "$height" + " (" + area + ")",
        input.mouseWorldX() + (if (input.mouseWorldX() > x1 * tilesize) offset else -offset),
        input.mouseWorldY() + (if (input.mouseWorldY() > y1 * tilesize) offset else -offset),
      )

      font.setColor(Color.white)
      font.getData().setScale(1f)
      font.setUseIntegerPositions(ints)
      Draw.z(z)
    }
  }

  var mode = InputMode.none

  var selectionTask = SelectionTask()

  fun isSelectionAvailable() = selectionTask.func != null

  fun selectionTask(
    maxLength: Int = 325799325,
    col1: Color = Pal.accentBack,
    col2: Color = Pal.accent,
    text: String? = null,
    func: SelectionTaskFunc,
  ) {
    selectionTask.apply {
      this.maxLength = maxLength
      this.col1 = col1
      this.col2 = col2
      this.text = text
      this.func = func
    }
  }

  fun world(x: Int, y: Int) = world(x.toFloat(), y.toFloat())

  fun world(cursorX: Float, cursorY: Float) = Core.input.mouseWorld(cursorX, cursorY)

  fun tile(pos: Vec2) = world.tile(World.toTile(pos.x), World.toTile(pos.y))

  override fun longPress(
    x: Float,
    y: Float,
  ): Boolean = false

  override fun keyDown(keycode: KeyCode): Boolean = false

  override fun keyUp(keycode: KeyCode): Boolean = false

  override fun keyTyped(character: Char): Boolean = false

  override fun touchDown(
    screenX: Int,
    screenY: Int,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun touchUp(
    screenX: Int,
    screenY: Int,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun touchDragged(
    screenX: Int,
    screenY: Int,
    pointer: Int,
  ): Boolean = false

  override fun mouseMoved(
    screenX: Int,
    screenY: Int,
  ): Boolean = false

  override fun scrolled(
    amountX: Float,
    amountY: Float,
  ): Boolean = false

  override fun touchDown(
    x: Float,
    y: Float,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun tap(
    x: Float,
    y: Float,
    count: Int,
    button: KeyCode,
  ): Boolean = false

  override fun fling(
    velocityX: Float,
    velocityY: Float,
    button: KeyCode,
  ): Boolean = false

  override fun pan(
    x: Float,
    y: Float,
    deltaX: Float,
    deltaY: Float,
  ): Boolean = false

  override fun panStop(
    x: Float,
    y: Float,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun zoom(
    initialDistance: Float,
    distance: Float,
  ): Boolean = false

  override fun pinch(
    initialPointer1: Vec2,
    initialPointer2: Vec2,
    pointer1: Vec2,
    pointer2: Vec2,
  ): Boolean = false

  override fun pinchStop() {}
}
