package peroxicore.ui

import arc.input.*
import arc.math.*
import arc.math.geom.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.gen.*
import mindustry.ui.*

open class FloatingTable() : Table() {
  val dragButton: ImageButton
  var dragging = false

  init {
    dragButton = ImageButton(Icon.move, Styles.cleari)
    add(dragButton).uniformX().uniformY().fill()
    dragButton.addListener(
      object : InputListener() {
        var dragX = 0f
        var dragY = 0f

        override fun touchDown(
          event: InputEvent,
          x: Float,
          y: Float,
          pointer: Int,
          button: KeyCode,
        ): Boolean {
          dragging = true
          dragX = x
          dragY = y
          return true
        }

        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
          dragging = false
        }

        override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
          positionParent(x, y)
        }
      }
    )

    update {
      color.a = if (dragging) 0.5f else 1f

      val pos = localToParentCoordinates(Tmp.v1.set(0f, 0f))
      setPosition(
        Mathf.clamp(pos.x, getPrefWidth() / 2f, parent.getWidth() - getPrefWidth() / 2f),
        Mathf.clamp(pos.y, getPrefHeight() / 2f, parent.getHeight() - getPrefHeight() / 2f),
      )
    }
  }

  fun positionParent(x: Float, y: Float) {
    if (parent == null) return
    val pos = localToParentCoordinates(Tmp.v1.set(x, y))
    setPosition(
      Mathf.clamp(pos.x, getPrefWidth() / 2f, parent.getWidth() - getPrefWidth() / 2f),
      Mathf.clamp(pos.y, getPrefHeight() / 2f, parent.getHeight() - getPrefHeight() / 2f),
    )
  }
}
