package peroxicore.ponder.record

/**
 * 录制结果导出器。
 *
 * 新增格式时实现本接口，并注册到 [PonderRecorder.exporters]，导出面板会自动列出。
 * 实现不需要关心数据来源，直接读取 [PonderRecorder] 中的初始地图与关键帧即可。
 */
interface SceneExporter {
  /** 展示名称。 */
  val name: String

  /** 保存时使用的扩展名，不带点。 */
  val extension: String

  /**
   * 导出内容对应的语言标签（等同 fenced code block 的 info 串），用于预览时挑选高亮器。
   * 空串表示不指定。
   */
  val language: String
    get() = ""

  /** 生成导出内容。 */
  fun build(): String
}

/** Kotlin DSL 场景导出。 */
object KotlinSceneExporter : SceneExporter {
  override val name: String = "Kotlin DSL"
  override val extension: String = "kt"
  override val language: String = "kotlin"

  override fun build(): String = PonderRecorder.buildKotlinDsl()
}
