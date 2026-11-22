package peroxicore.ponder.record

import arc.*
import arc.scene.ui.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import peroxicore.ui.*

/**
 * 导出内容预览对话框：用 [PCodeBlock] 渲染源码，并按 [SceneExporter.language] 选择高亮器。
 *
 * 默认解析器只认识 Ciallo：`ciallo` 导出会走 `CialloCodeHighlighter`，其它语言（例如 Kotlin）
 * 暂不高亮，返回 `null` 即可。想给新语言加高亮时替换 [highlighterResolver]。
 */
open class CodePreviewDialog : BaseDialog("") {
  /** 按语言标签挑选高亮器；`null` 表示不高亮。默认认识 Ciallo 与 Kotlin。 */
  var highlighterResolver: CodeHighlighterResolver = CodeHighlighterResolver.DEFAULT

  /** 代码块渐变/前景与底色；默认 Tokyo Night。 */
  var palette: CodePalette = CodePalette()

  /** 代码块的文字渲染器；`null` 时用 arc Label 版默认实现（跟随 [palette]）。 */
  var textFactory: CodeTextFactory? = null

  /** 对话框宽高占视口的最大比例。 */
  var maxViewportRatio: Float = 0.85f

  init {
    shouldPause = true
    addCloseButton()
  }

  fun show(titleText: String, exporter: SceneExporter, text: String) {
    show(titleText, exporter.language, text)
  }

  fun show(titleText: String, language: String, text: String) {
    title.setText(titleText)

    cont.clearChildren()
    cont.margin(12f)

    val tokens = highlighterResolver.resolve(language)?.highlight(text) ?: emptyList()
    val block = PCodeBlock(
      code = text,
      textFactory = textFactory ?: LabelCodeTextFactory(palette = palette),
      tokens = tokens,
      language = language,
    )

    // 外层只做纵向滚动：禁用 X 让 ScrollPane 把 block 宽度压成可视宽度，水平的溢出交给
    // PCodeBlock 自带的横向 ScrollPane，这样右上角的语言标签和复制按钮始终可见。
    // 用 top().left() 而不是 grow()，代码块只包住代码本身，剩余空间留给对话框背景。
    val pane = ScrollPane(block, Styles.defaultPane).apply {
      isScrollingDisabledX = true
    }
    cont.top().left()
    cont.add(pane).growX().top().left()

    super.show()
  }

  // Dialog.pack() 会用 prefWidth/prefHeight 定尺寸，而 PCodeBlock 的 pref 宽度就是最长一行的
  // 自然宽度；不夹紧的话长代码会把对话框撑出屏幕。
  override fun getPrefWidth(): Float {
    val natural = super.getPrefWidth()
    val cap = viewportWidth() * maxViewportRatio
    return natural.coerceAtMost(cap).coerceAtLeast(minOf(480f, cap))
  }

  override fun getPrefHeight(): Float {
    val natural = super.getPrefHeight()
    val cap = viewportHeight() * maxViewportRatio
    return natural.coerceAtMost(cap).coerceAtLeast(minOf(320f, cap))
  }

  private fun viewportWidth(): Float = (Core.scene?.width ?: Core.graphics.width.toFloat())

  private fun viewportHeight(): Float = (Core.scene?.height ?: Core.graphics.height.toFloat())
}
