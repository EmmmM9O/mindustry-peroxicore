package peroxicore.scene

import arc.*
import arc.scene.*

/**
 * 完全接管 [Core.scene] 的 Scene 实现。
 *
 * Mindustry 在 `UI.loadSync` 里创建原生 [Scene] 并注册到输入处理器，本类用 [install]
 * 把它替换掉：原有 root 子元素、边距、viewport、焦点和动作请求渲染设置都会迁移过来。
 */
class POCScene : Scene(){
  companion object{
    /**
     * 把 [Core.scene] 换成 [POCScene]。
     *
     * 必须在原生 Scene 已创建（客户端 UI 加载完成）之后调用；headless 或已替换时直接返回当前实例。
     */
    @JvmStatic
    fun install(): POCScene{
      val old = Core.scene
      if(old is POCScene) return old

      val scene = POCScene()
      if(old != null){
        scene.setViewport(old.viewport)
        scene.marginLeft = old.marginLeft
        scene.marginRight = old.marginRight
        scene.marginTop = old.marginTop
        scene.marginBottom = old.marginBottom
        scene.setActionsRequestRendering(old.actionsRequestRendering)

        old.elements.toArray().forEach(scene::add)

        old.keyboardFocus?.let(scene::setKeyboardFocus)
        old.scrollFocus?.let(scene::setScrollFocus)

        Core.input.removeProcessor(old)
      }

      Core.scene = scene
      Core.input.addProcessor(scene)
      scene.resize(Core.graphics.width, Core.graphics.height)
      return scene
    }
  }
}
