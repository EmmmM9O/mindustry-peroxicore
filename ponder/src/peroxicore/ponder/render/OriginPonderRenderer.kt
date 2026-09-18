package peroxicore.ponder.render

import arc.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.graphics.gl.*
import mindustry.*
import mindustry.core.*
import mindustry.game.*
import mindustry.game.EventType.*
import mindustry.graphics.*
import peroxicore.ponder.*
import peroxicore.utils.handler.*
import universe.util.reflect.*

class DefaultOverlayRenderer : OverlayRenderer() {
  init {
    EventsHandler.removeLast<WorldLoadEvent>()
    EventsHandler.removeLast<CoreChangeEvent>()
  }

  override fun drawBottom() {
    PonderCore.input.drawBottom()
  }

  override fun drawTop() {
    PonderCore.input.drawTop()
  }
}

var BlockRenderer.hadMapLimit by accessBoolean("hadMapLimit")
var Renderer.blocks_: BlockRenderer by accessField("blocks")
var Renderer.overlays_: OverlayRenderer by accessField("overlays")

class OriginPonderRenderer : PonderRenderer {
  lateinit var buffer: FrameBuffer

  lateinit var blocks: BlockRenderer
  lateinit var originBlocks: BlockRenderer

  override val camera = Camera()
  lateinit var originCamera: Camera

  val tileChange = TileChangeEvent()
  val preChange = TilePreChangeEvent()

  lateinit var originOverlays: OverlayRenderer
  lateinit var overlays: OverlayRenderer

  var originShown = false

  override val texture: Texture
    get() = buffer.texture

  val size: Float
    get() =
      if (Core.graphics.width > Core.graphics.height) {
        Core.graphics.height * 0.7f
      } else {
        Core.graphics.width * 0.8f
      }

  val resolution
    get() =
      scaleToMax(
        PonderCore.tiles.width.toFloat(),
        PonderCore.tiles.height.toFloat(),
        size
      )

  override fun setup() {}

  override fun init() {
    buffer = FrameBuffer()
    val (w, h) = resolution
    buffer.resize(w.toInt(), h.toInt())

    overlays = DefaultOverlayRenderer()

    blocks = BlockRenderer()
    // 删去BlockRenderer的部分事件
    EventsHandler.removeLast<WorldLoadEvent>()
    EventsHandler.removeLast<WorldLoadEvent>() // Floor
    val tpc = EventsHandler.removeLast<TilePreChangeEvent>()!!
    val tc = EventsHandler.removeLast<TileChangeEvent>()!!
    EventsHandler.removeLastRun(Trigger.newGame)

    Events.on(PonderTilePreChangeEvent::class.java) { event ->
      tpc.get(preChange.set(event.tile))
    }

    Events.on(PonderTileChangeEvent::class.java) { event ->
      tc.get(tileChange.set(event.tile))
    }

    Events.run(EventType.Trigger.draw) {
      if (!PonderCore.rendering) return@run
      Draw.draw(Layer.end) {
        buffer.end()
      }
    }
  }

  override fun render() {
    val (w, h) = resolution
    if (buffer.resizeCheck(w.toInt(), h.toInt())) {
      PonderCore.ponder.rebuildImage()
    }
    buffer.begin(Color.clear)
    // ScreenSampler.enable = false

    Vars.renderer.draw()

    // ScreenSampler.enable = true
  }

  override fun begin() {
    if (Core.camera != camera) originCamera = Core.camera
    Core.camera = camera
    if (Vars.renderer.blocks != blocks) originBlocks = Vars.renderer.blocks
    if (Vars.renderer.overlays != overlays) originOverlays = Vars.renderer.overlays
    Vars.renderer.blocks_ = blocks
    Vars.renderer.overlays_ = overlays
    originShown = Vars.ui.hudfrag.shown
    Vars.ui.hudfrag.shown = false
  }

  override fun end() {
    Core.camera = originCamera
    Vars.renderer.blocks_ = originBlocks
    Vars.renderer.overlays_ = originOverlays
    Vars.ui.hudfrag.shown = originShown
  }

  override fun reload() {
    blocks.reload()
    blocks.floor.reload()
    blocks.hadMapLimit = false
  }

  override fun beginWorld() {}

  override fun beforeWorld() {
    camera.width = Vars.world.unitWidth().toFloat()
    camera.height = Vars.world.unitHeight().toFloat()
    camera.position.set(camera.width / 2f - 4f, camera.height / 2f - 4f)
  }

  override fun endWorld() {}
}
