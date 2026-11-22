package peroxicore.ponder.record

import arc.util.*
import mindustry.*
import mindustry.content.*
import mindustry.ctype.*
import mindustry.type.*
import mindustry.world.*
import mindustry.world.blocks.environment.*
import peroxicore.ciallopoet.*

/**
 * 把录制结果导出为 Ciallo 场景脚本。
 *
 * 输出遵循 Ponder 的 Ciallo 绑定形状：关键帧里的放置写成
 * `build @Blocks.x config @Items.y rotation 1 at(4, 5)`，拆除写成
 * `setBlock @Blocks.air at(4, 5)`，地面/配置分别写成 `setFloor` / `configure`。
 * 初始地图用 `world(w, h) { ... }` 还原，能合并的矩形用 `region(...) fillFloor/fillBlock/fillOverlay` 批量写入。
 */
object CialloSceneExporter : SceneExporter {
  override val name: String = "Ciallo"
  override val extension: String = "ciallo"
  override val language: String = "ciallo"

  override fun build(): String = buildScript()

  private fun buildScript(): String {
    val recorder = PonderRecorder
    val map = recorder.initial ?: return "// 还没有框选录制区域\n"
    val sceneContent = recorder.content
    val head =
      if (sceneContent is Block) "Blocks.${recorder.contentName(sceneContent.name)}"
      else "Blocks.itemSource"

    val scene = CialloBlockSpec.builder()
    if (sceneContent !is Block) {
      scene.addComment("原场景内容不是方块: ${sceneContent?.name ?: "unknown"}，请自行替换 header")
    }
    scene.addStatement("duration %L", tick(recorder.duration))

    val worldBody = CialloBlockSpec.builder()
    appendMap(worldBody, map, recorder)
    scene.addChain(
      CialloChainSpec.builder("world")
        .addArgument("%L", map.width)
        .addArgument("%L", map.height)
        .block(worldBody.build())
        .build()
    )

    recorder.keyframes
      .sortedBy { it.tick }
      .forEach { keyframe ->
        val body = CialloBlockSpec.builder()
        keyframe.actions.forEach { appendAction(body, it, recorder) }
        scene.addChain(
          CialloChainSpec.builder("frame")
            .addArgument("%L", tick(keyframe.tick))
            .block(body.build())
            .build()
        )
      }

    val script =
      CialloChainSpec.symbolBuilder(head).addCall("scene", scene.build()).build()
    return CialloFileSpec.builder("RecordedScene").addStatement(script).build().formatted()
  }

  private fun appendMap(
    out: CialloBlockSpec.Builder,
    map: RecordedMap,
    recorder: PonderRecorder,
  ) {
    // 整张地图只有一种地面时，用 fillFloor 一行代替逐矩形填充。
    val common = commonFloor(map)
    common?.let { id ->
      contentRef(Vars.content.block(id), recorder)?.let { out.addStatement("fillFloor %A", it) }
    }

    map.floors.forEach { rect ->
      if (rect.data.toInt() == common) return@forEach
      val ref = contentRef(Vars.content.block(rect.data.toInt()), recorder) ?: return@forEach
      out.addChain(regionChain(rect).addMember("fillFloor").addValue(CialloCodeBlock.of("%A", ref)).build())
    }

    map.overlays.forEach { rect ->
      val ref = contentRef(Vars.content.block(rect.data.toInt()), recorder) ?: return@forEach
      out.addChain(
        regionChain(rect).addMember("fillOverlay").addValue(CialloCodeBlock.of("%A", ref)).build()
      )
    }

    map.blocks.forEach { rect ->
      val block = Vars.content.block(rect.id) ?: return@forEach
      if (block === Blocks.air) return@forEach
      val ref = contentRef(block, recorder) ?: return@forEach
      val config = rect.config?.let { cialloConfig(it) }
      if (rect.config != null && config == null) {
        out.addComment("不支持的初始配置: ${rect.config!!.javaClass.simpleName} @ (${rect.x}, ${rect.y})")
      }
      val whole = rect.width * rect.height > 1
      when {
        // 单格方块成片且无配置：按矩形批量填充。
        whole && block.size <= 1 && config == null ->
          out.addChain(
            regionChain(rect.x, rect.y, rect.right, rect.bottom)
              .addCall(
                "fillBlock",
                CialloCodeBlock.of("%A", ref),
                CialloCodeBlock.of("%L", rect.rotation),
              )
              .build()
          )
        else -> {
          for (x in rect.x until rect.right) {
            for (y in rect.y until rect.bottom) {
              out.addChain(setBlockChain(ref, rect.rotation, x, y))
              config?.let { out.addChain(configureChain(it, x, y)) }
            }
          }
        }
      }
    }
  }

  private fun appendAction(
    out: CialloBlockSpec.Builder,
    action: RecordedAction,
    recorder: PonderRecorder,
  ) {
    when (action) {
      is RecordedAction.Place -> {
        val ref = contentRef(Vars.content.block(action.block), recorder) ?: return
        val config = action.config?.let { cialloConfig(it) }
        if (action.config != null && config == null) {
          out.addComment(
            "不支持的配置: ${action.config!!.javaClass.simpleName} @ (${action.x}, ${action.y})"
          )
        }
        // 关键帧用 build 以保留建造动画：build @Blocks.x config ... rotation ... at(x, y)
        val chain =
          CialloChainSpec.builder("build")
            .addValueArgument("%A", ref)
            .apply {
              if (config != null) {
                addMember("config")
                addValue(config)
              }
            }
            .apply {
              if (action.rotation != 0) {
                addMember("rotation")
                addValue(CialloCodeBlock.of("%L", action.rotation))
              }
            }
            .addCall("at", action.x, action.y)
            .build()
        out.addChain(chain)
      }
      is RecordedAction.Remove ->
        out.addChain(
          CialloChainSpec.builder("setBlock")
            .addValueArgument("%A", "Blocks.air")
            .addCall("at", action.x, action.y)
            .build()
        )
      is RecordedAction.Configure -> {
        val config = cialloConfig(action.value) ?: run {
          out.addComment(
            "不支持的配置: ${action.value?.javaClass?.simpleName ?: "null"} @ (${action.x}, ${action.y})"
          )
          return
        }
        out.addChain(configureChain(config, action.x, action.y))
      }
    }
  }

  private fun commonFloor(map: RecordedMap): Int? {
    val area = HashMap<Int, Int>()
    map.floors.forEach { rect ->
      val id = rect.data.toInt()
      area[id] = (area[id] ?: 0) + rect.width * rect.height
    }
    return area.entries.maxByOrNull { it.value }?.takeIf { it.value == map.width * map.height }?.key
  }

  private fun regionChain(rect: TileRect): CialloChainSpec.Builder =
    regionChain(rect.x, rect.y, rect.right, rect.bottom)

  private fun regionChain(
    x: Int,
    y: Int,
    right: Int,
    bottom: Int,
  ): CialloChainSpec.Builder =
    CialloChainSpec.builder("region")
      .addArgument("%L", x)
      .addArgument("%L", y)
      .addArgument("%L", right - 1)
      .addArgument("%L", bottom - 1)

  /** 时间轴上的时刻：整数写整数，否则写小数。 */
  private fun tick(value: Float): Any =
    if (value == value.toInt().toFloat()) value.toInt() else value

  private fun setBlockChain(
    ref: String,
    rotation: Int,
    x: Int,
    y: Int,
  ): CialloChainSpec =
    CialloChainSpec.builder("setBlock")
      .addValueArgument("%A", ref)
      .apply {
        if (rotation != 0) {
          addMember("rotation")
          addValue(CialloCodeBlock.of("%L", rotation))
        }
      }
      .addCall("at", x, y)
      .build()

  private fun configureChain(
    config: CialloCodeBlock,
    x: Int,
    y: Int,
  ): CialloChainSpec =
    CialloChainSpec.builder("configure")
      .addValueArgumentCode(config)
      .addCall("at", x, y)
      .build()

  /** 内容引用，例如 `Blocks.titaniumConveyor`；不支持的返回 null。 */
  private fun contentRef(
    content: UnlockableContent?,
    recorder: PonderRecorder,
  ): String? =
    when (content) {
      null -> null
      is Block -> "Blocks.${recorder.contentName(content.name)}"
      is Item -> "Items.${recorder.contentName(content.name)}"
      is Liquid -> "Liquids.${recorder.contentName(content.name)}"
      is UnitType -> "Units.${recorder.contentName(content.name)}"
      is StatusEffect -> "StatusEffects.${recorder.contentName(content.name)}"
      else -> null
    }

  /** 把任意 config 值转成 Ciallo 源码；不能表达时返回 null。 */
  private fun cialloConfig(value: Any?): CialloCodeBlock? =
    when (value) {
      null -> null
      is String -> CialloCodeBlock.of("%S", value)
      is Boolean,
      is Int -> CialloCodeBlock.of("%L", value)
      is Float,
      is Double -> CialloCodeBlock.of("%L", (value as Number).toFloat())
      is UnlockableContent -> {
        val ref = contentRef(value, PonderRecorder) ?: return null
        CialloCodeBlock.of("%A", ref)
      }
      else -> null
    }
}
