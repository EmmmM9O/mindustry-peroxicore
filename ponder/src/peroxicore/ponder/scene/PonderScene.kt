package peroxicore.ponder.scene

import arc.*
import arc.scene.style.*
import arc.scene.ui.layout.*
import mindustry.*
import mindustry.ctype.*
import mindustry.type.*
import mindustry.world.*

data class PonderTag(
  val tag: String,
)

interface PonderScene {
  val name: String
  val timeline: Timeline
  val icon: Drawable
  val tags: Set<PonderTag>

  // Click info button
  fun info() {}

  fun buildDialog(table: Table) {}
}

fun UnlockableContent.ponderType() =
  when (this) {
    is Block -> this.category.name.let { Core.bundle.get("ponder.$it", it) }
    else -> "UNKNOWN"
  }

open class UnlockableContentPonder(
  val content: UnlockableContent,
  override val timeline: Timeline,
  override val tags: Set<PonderTag> = mutableSetOf(),
) : PonderScene {
  override val name: String
    get() = "${content.localizedName}  ${content.ponderType()}"

  override val icon: Drawable
    get() = TextureRegionDrawable(content.fullIcon)

  override fun info() {
    Vars.ui.content.show(content)
  }
}
