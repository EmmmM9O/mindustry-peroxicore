package peroxicore.ui

import arc.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*

import mindustry.*
import mindustry.gen.*
import mindustry.ui.*

class DebugTools : FloatingTable() {
  var visibleC = false
  val visButton: ImageButton
  val contentTable: Table

  init {
    reset()
    visButton =
      ImageButton(
        ImageButton.ImageButtonStyle(Styles.cleari).apply {
          imageUp = Icon.eyeSmall
          imageChecked = Icon.eyeOffSmall
        }
      )
    visButton.changed {
      visibleC = !visibleC
      contentTable.visible = visibleC
    }

    contentTable = Table(Styles.black6).apply {}
    contentTable.visible = visibleC

    table { table ->
        table.table().row()
        table
          .table { buttons ->
            buttons.add(dragButton).size(48f)
            buttons.add(visButton).size(48f)
          }
          .height(48f)
          .center()
        table.table().row()
      }
      .uniformX()
      .uniformY()
      .height(48f)
      .fill()
    row()


    add(contentTable).width(400f).height(400f)
    Core.scene.add(this)
  }
}
