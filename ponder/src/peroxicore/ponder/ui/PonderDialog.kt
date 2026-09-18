package peroxicore.ponder.ui

import arc.*
import arc.func.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.math.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.event.*
import arc.scene.style.*
import arc.scene.ui.*
import arc.scene.ui.ImageButton.*
import arc.scene.ui.layout.*
import arc.util.pooling.*
import mindustry.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import peroxicore.ponder.*
import peroxicore.ponder.scene.*
import kotlin.Unit

class TimelineBar : Element() {
  var barName = ""
  var value = 0f
  var lastValue = 0f
  var blink = 0f
  var outlineRadius = 0f
  val blinkColor = Color()
  val outlineColor = Color()
  var timeline: Timeline? = null
    set(value) {
      field = value
      snap()
    }

  val bw =
    Core.atlas
      .find("bar-top")
      .width
      .toFloat()

  var fraction =
    Floatp {
      timeline?.run { PonderCore.currentTick / duration } ?: 0f
    }

  fun updateName(timeline: Timeline?) =
    timeline?.let {
      "${PonderCore.currentTick.toInt()} / ${timeline.duration.toInt()} x${it.speed}"
    } ?: "[NULL]"

  init {
    lastValue = fraction.get()
    value = fraction.get()

    this.blinkColor.set(Color.white)
    setColor(Color.white)
    addListener(
      object : ClickListener() {
        override fun clicked(
          event: InputEvent,
          x: Float,
          y: Float,
        ) {
          var target = -1
          timeline?.apply {
            marks.forEachIndexed { index, mark ->
              val dx = width * mark.tick / duration
              if (
                scissor
                  .set(dx - hitWidth / 2f, 0f, hitWidth, fullWidth + barHeight + tickOver)
                  .contains(x, y)
              ) {
                target = index + 1
                return@apply
              }
            }
          }
          if (target != -1) {
            PonderCore.jumpTo(target)
          }
        }
      }
    )
  }

  fun reset(value: Float) {
    this.value = value
    lastValue = value
    blink = value
  }

  fun snap() {
    lastValue = fraction.get()
    value = fraction.get()
  }

  fun outline(
    color: Color,
    stroke: Float,
  ): TimelineBar {
    outlineColor.set(color)
    outlineRadius = Scl.scl(stroke)
    return this
  }

  fun flash() {
    blink = 1f
  }

  fun blink(color: Color): TimelineBar {
    blinkColor.set(color)
    return this
  }

  val timelineBack = (Tex.whiteui as TextureRegionDrawable)
  val tickWidth = 6f
  val tickOver = 3f
  val iconWidth = 20f
  val fullWidth = 60f
  val hitWidth = 80f
  val barHeight = 20f

  override fun draw() {
    var computed = Mathf.clamp(fraction.get())

    if (lastValue > computed) {
      blink = 1f
      lastValue = computed
    }

    if (lastValue.isNaN()) lastValue = 0f
    if (lastValue.isInfinite()) lastValue = 1f
    if (value.isNaN()) value = 0f
    if (value.isInfinite()) value = 1f
    if (computed.isNaN()) computed = 0f
    if (computed.isInfinite()) computed = 1f

    blink = Mathf.lerpDelta(blink, 0f, 0.2f)
    value = Mathf.lerpDelta(value, computed, 0.15f)

    val bar = Tex.bar

    if (outlineRadius > 0) {
      Draw.color(outlineColor)
      bar.draw(
        x - outlineRadius,
        y - outlineRadius,
        width + outlineRadius * 2,
        barHeight + outlineRadius * 2
      )
    }

    Draw.colorl(0.1f)
    Draw.alpha(parentAlpha)
    bar.draw(x, y, width, barHeight)
    Draw.color(color, blinkColor, blink)
    Draw.alpha(parentAlpha)

    val top = Tex.barTop
    val topWidth = width * value

    if (topWidth > bw) {
      top.draw(x, y, topWidth, barHeight)
    } else {
      scissor.set(x, y, topWidth, barHeight)
      if (ScissorStack.push(scissor)) {
        top.draw(x, y, bw, barHeight)
        ScissorStack.pop()
      }
    }

    Draw.color(Pal.accent)

    timeline?.apply {
      marks.forEach {
        val dx = width * it.tick / duration
        timelineBack.draw(x + dx - tickWidth / 2f, y, tickWidth, barHeight)
        timelineBack.draw(x + dx - iconWidth / 2f, y + barHeight, iconWidth, tickOver)
        it.icon?.let { icon ->
          timelineBack.draw(x + dx - fullWidth / 2f, y + barHeight + tickOver, fullWidth, fullWidth)
        }
      }
    }

    Draw.color(Pal.gray)

    timeline?.apply {
      marks.forEach {
        val dx = width * it.tick / duration
        it.icon?.draw(x + dx - fullWidth / 2f, y + barHeight + tickOver, fullWidth, fullWidth)
      }
    }

    Draw.color()

    val font = Fonts.outline
    val lay = Pools.obtain(GlyphLayout::class.java, ::GlyphLayout)

    timeline?.let { barName = updateName(it) }

    lay.setText(font, barName)

    font.setColor(1f, 1f, 1f, 1f)
    font.cache.clear()
    font.cache.addText(
      barName,
      x + width / 2f - lay.width / 2f,
      y + height / 2f + lay.height / 2f + 1
    )
    font.cache.draw(parentAlpha)

    Pools.free(lay)
  }

  companion object {
    private val scissor = Rect()
  }
}

open class PonderDialog :
  BaseDialog(
    "",
    DialogStyle().apply {
      stageBackground = Styles.black9
      background = Tex.windowEmpty
      titleFont = Fonts.def
      titleFontColor = Pal.accent
    }
  ) {
  var marginSize = 80f

  val view = Table()
  val timelineBar = TimelineBar()
  val style1: ImageButtonStyle =
    object : ImageButtonStyle() {
      init {
        down = Styles.flatDown
        up = Styles.black6
        over = Styles.flatOver
        disabled = Styles.black6
      }
    }
  val rightBar =
    Table { bar ->
      bar.right().bottom()

      bar
        .button(Icon.left, style1) {
          PonderCore.previous()
        }.size(marginSize)
        .get()
        .resizeImage(marginSize)
      bar
        .button(Icon.right, style1) {
          PonderCore.next()
        }.size(marginSize)
        .get()
        .resizeImage(marginSize)
      bar
        .button(Icon.refresh, style1) {
          PonderCore.restart()
        }.size(marginSize)
        .get()
        .resizeImage(marginSize)
      bar
        .button(Icon.info, style1) {
          PonderCore.scene?.info()
        }.size(marginSize)
        .get()
        .resizeImage(marginSize)
    }

  val drawable = TextureRegionDrawable(TextureRegion())
  val image = Image(drawable)

  var display = false

  fun load() {
    rebuildBar()
    rebuildImage()
    rebuildView()
  }

  init {
    load()
    onResize(this::rebuildBar)
    onResize(this::rebuildView)
    onResize(this::setup)
    shown(this::setup)
    hidden(this::onHidden)
  }

  fun onHidden() {
    PonderCore.endDialog()
    display = false
  }

  var lastDisplay: Displayable? = null

  fun setup() {
    val current = PonderCore.scene
    if (current == null) {
      hide()
      return
    }
    timelineBar.timeline = current.timeline
    PonderCore.beginDialog()
    display = true
    Vars.ui.minimapfrag.hide()

    clearChildren()
    margin(0f)

    stack(
      view,
      Table { main ->
        main.top().left()
        main.table { info ->
          info.image(current.icon).size(marginSize)
          info
            .table { text ->
              text
                .table { top ->
                  top.add("思索......", 2f).left().style(Styles.outlineLabel)
                  top.add().growX()
                }.uniformX()
                .growX()
                .row()
              text
                .table { bottom ->
                  bottom.add(current.name, 1.5f).style(Styles.outlineLabel)
                }.uniformX()
                .growX()
            }.height(marginSize)
        }
      },
      Table { infoBar ->
        infoBar.bottom().right().marginBottom(marginSize)
        infoBar.add(Table(Styles.black6)).update { info ->
          val hovered = PonderCore.hovered()
          if (lastDisplay == hovered) return@update
          lastDisplay = hovered
          info.clearChildren()
          if (hovered != null) {
            hovered.display(info)
          }
        }
      },
      buttons
    ).grow()
    current.buildDialog(this)
  }

  fun rebuildImage() {
    val texture = PonderCore.renderer.texture
    drawable.region.set(texture)
    drawable.region.set(0f, 1f, 1f, 0f)
  }

  fun rebuildView() {
    view.clearChildren()
    view.center()
    val gw = Core.graphics.width
    val gh = Core.graphics.height
    val size = if (gw > gh) gh * 0.7f else gw * 0.8f
    val (w, h) =
      scaleToMax(PonderCore.tiles.width.toFloat(), PonderCore.tiles.height.toFloat(), size)
    view.add(image).size(w, h)
  }

  fun rebuildBar() {
    buttons.clearChildren()
    buttons.bottom()
    buttons
      .button(Icon.exit, style1, this::hide)
      .left()
      .size(marginSize)
      .get()
      .resizeImage(marginSize)
    buttons.add(timelineBar).height(marginSize).growX()
    buttons.add(rightBar).right()
  }
}
