package peroxicore.ui

import arc.scene.*
import arc.scene.ui.*
import arc.util.pooling.*
import mindustry.ui.*
import org.commonmark.node.*
import universe.ui.markdown.*

/**
 * [Markdown.MarkdownDraw] adapter that lays a [PCodeBlock] out inside a markdown document,
 * mirroring the markdown module's own `DrawCodeBlock`.
 */
class PCodeBlockDraw private constructor() : Markdown.MarkdownDraw(), Markdown.ActivityDrawer {
  companion object {
    fun get(
      textFactory: CodeTextFactory,
      code: String,
      tag: String,
      sliderStyle: ScrollPane.ScrollPaneStyle,
      fontScale: Float,
      highlighter: CodeHighlighter?,
    ): PCodeBlockDraw = Pools.obtain(PCodeBlockDraw::class.java) { PCodeBlockDraw() }.apply {
      this.textFactory = textFactory
      this.code = code
      this.tag = tag
      this.sliderStyle = sliderStyle
      this.fontScale = fontScale
      this.highlighter = highlighter
    }
  }

  /** Language tag of the fenced block (the info string). */
  var tag: String = ""

  private var textFactory: CodeTextFactory = LabelCodeTextFactory()
  private var code: String = ""
  private var sliderStyle: ScrollPane.ScrollPaneStyle = Styles.noBarPane
  private var fontScale: Float = 1f
  private var highlighter: CodeHighlighter? = null

  private var realWidth = 0f
  private var realHeight = 0f
  private var block: PCodeBlock? = null

  override val activeElement: Element
    get() = block ?: error("PCodeBlockDraw.setup was not called")

  override fun reset() {
    super.reset()
    tag = ""
    textFactory = LabelCodeTextFactory()
    code = ""
    sliderStyle = Styles.noBarPane
    fontScale = 1f
    highlighter = null
    realWidth = 0f
    realHeight = 0f
    block = null
  }

  override fun prefWidth(): Float = realWidth

  override fun prefHeight(): Float = realHeight

  override fun setup(scope: RendererContext.Scope) {
    realWidth = scope.boundX - scope.marginRight - offsetX

    val tokens = highlighter?.highlight(code) ?: emptyList()
    val b = PCodeBlock(code, textFactory, tokens, tag, fontScale, sliderStyle = sliderStyle)
    // Mirror the markdown module's DrawCodeBlock: the block is as tall as its content (text,
    // gutter and block margin), and the scroll pane gets the width handed down by the layout.
    b.validate()
    realHeight = b.prefHeight
    block = b
  }

  override fun draw(x: Float, y: Float) {}
}

/**
 * [MarkdownProvider] rendering fenced and indented code blocks with [PCodeBlock].
 *
 * Pass a [CodeTextFactory] to change how the source is drawn; the `lwjgl` font module provides
 * `peroxicore.font.PLabelCodeTextFactory`, which renders through a ligature-aware `PLabel`. When no
 * factory is given, the markdown style's `codeFont` is used through [LabelCodeTextFactory].
 *
 * [highlighterResolver] picks a [CodeHighlighter] from the fenced block's language tag. It
 * defaults to [CodeHighlighterResolver.DEFAULT], which highlights untagged and
 * ```` ```ciallo ```` blocks as Ciallo and ```` ```kotlin ````/`kt`/`kts` as Kotlin.
 */
open class PCodeBlockProvider @JvmOverloads constructor(
  var textFactory: CodeTextFactory? = null,
  var highlighterResolver: CodeHighlighterResolver? = CodeHighlighterResolver.DEFAULT,
) : BaseProvider() {

  override fun RendererContext.add(node: FencedCodeBlock) = codeBlock(node.literal, node.info ?: "")

  override fun RendererContext.add(node: IndentedCodeBlock) = codeBlock(node.literal, "")

  private fun RendererContext.codeBlock(literal: String, tag: String) {
    withScope(box = mdStyle.codeBlockBox) {
      mdStyle.codeFont.applyFont()

      val language = tag.trim()
      draw(
        PCodeBlockDraw.get(
          textFactory ?: LabelCodeTextFactory(mdStyle.codeFont.fontModifier ?: Fonts.monospace),
          literal.removeSuffix("\n"),
          language,
          mdStyle.sliderStyle,
          fontScale,
          highlighterResolver?.resolve(language),
        )
      )
    }
  }
}
