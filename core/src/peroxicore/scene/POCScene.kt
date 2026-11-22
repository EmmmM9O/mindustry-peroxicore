package peroxicore.scene

import arc.*
import arc.func.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.input.*
import arc.math.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.event.*
import arc.scene.style.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.struct.*
import arc.util.*
import arc.util.pooling.*
import arc.util.pooling.Pool.*
import arc.util.viewport.*

/**
 * [Scene] 的完整 Kotlin 移植，用来接管 [Core.scene]。
 *
 * 与 Java 版逐方法对应：公开 API 全部重写，原本 private 的字段与辅助函数也一并搬过来，
 * 只有 [root] 仍来自父类（Java 里是 final，无法自己新建）。因此行为不再受父类私有状态影响。
 */
class POCScene : Scene{
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
        scene.setViewport(old.getViewport())
        scene.marginLeft = old.marginLeft
        scene.marginRight = old.marginRight
        scene.marginTop = old.marginTop
        scene.marginBottom = old.marginBottom
        scene.setActionsRequestRendering(old.getActionsRequestRendering())

        // copy first: addChild reparents, which mutates the old root's child list
        old.getElements().toArray().forEach(scene::add)

        old.getKeyboardFocus()?.let(scene::setKeyboardFocus)
        old.getScrollFocus()?.let(scene::setScrollFocus)

        Core.input.removeProcessor(old)
      }

      Core.scene = scene
      Core.input.addProcessor(scene)
      scene.resize(Core.graphics.width, Core.graphics.height)
      return scene
    }
  }

  private val styleDefaults = ObjectMap<Class<*>, Any?>()
  private val tempCoords = Vec2()
  private val pointerOverActors = arrayOfNulls<Element>(20)
  private val pointerTouched = BooleanArray(20)
  private val pointerScreenX = IntArray(20)
  private val pointerScreenY = IntArray(20)
  private val touchFocuses = SnapshotSeq<TouchFocus>(true, 4, TouchFocus::class.java)

  private var viewportC: Viewport = object : ScreenViewport(){
    override fun calculateScissors(batchTransform: Mat, area: Rect, scissor: Rect){
      ScissorStack.calculateScissors(
        this.camera, this.screenX.toFloat(), this.screenY.toFloat(),
        this.screenWidth.toFloat(), this.screenHeight.toFloat(),
        batchTransform, area, scissor
      )
    }
  }
  private var mouseScreenX = 0
  private var mouseScreenY = 0
  private var mouseOverElement: Element? = null
  private var keyFocus: Element? = null
  private var scrollFocusElement: Element? = null
  private var actionsRender = true

  init{
    viewportC.update(Core.graphics.width, Core.graphics.height, true)
  }

  constructor() : super()

  constructor(viewport: Viewport) : this(){
    viewportC = viewport
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getStyle(type: Class<T>): T{
    return (styleDefaults.get(type)
      ?: throw IllegalArgumentException("No default style for type: " + type.simpleName)) as T
  }

  override fun <T> hasStyle(type: Class<T>): Boolean{
    return styleDefaults.containsKey(type)
  }

  override fun <T> addStyle(type: Class<T>, style: T){
    styleDefaults.put(type, style)
  }

  override fun registerStyles(type: Class<*>){
    for(field in type.fields){
      if(!field.name.startsWith("default")) continue
      @Suppress("UNCHECKED_CAST")
      addStyle(field.type as Class<Any>, Reflect.get<Any>(field))
    }
  }

  override fun registerStyles(obj: Any){
    for(field in obj.javaClass.fields){
      if(!field.name.startsWith("default")) continue
      @Suppress("UNCHECKED_CAST")
      addStyle(field.type as Class<Any>, Reflect.get<Any>(obj, field))
    }
  }

  override fun getHoverElement(): Element?{
    return mouseOverElement
  }

  override fun hasField(): Boolean{
    return getKeyboardFocus() is TextField
  }

  override fun hasMouse(): Boolean{
    return getHoverElement() != null
  }

  override fun hasMouse(mousex: Float, mousey: Float): Boolean{
    return hit(mousex, mousey, true) != null
  }

  override fun hasDialog(): Boolean{
    return getScrollFocus() is Dialog || getKeyboardFocus()?.isDescendantOf { e -> e is Dialog } == true
  }

  override fun hasKeyboard(): Boolean{
    return getKeyboardFocus() != null
  }

  override fun hasScroll(): Boolean{
    return getScrollFocus() != null
  }

  override fun getDialog(): Dialog?{
    val keyboard = getKeyboardFocus()
    if(keyboard is Dialog) return keyboard
    val scroll = getScrollFocus()
    if(scroll is Dialog) return scroll
    return null
  }

  override fun draw(){
    val camera = getCamera()
    camera.update()

    if(!root.visible) return

    Draw.proj(camera)

    root.draw()
    Draw.flush()
  }

  override fun act(){
    act(Core.graphics.deltaTime)
  }

  override fun act(delta: Float){
    root.setBounds(marginLeft, marginBottom, getWidth() - marginLeft - marginRight, getHeight() - marginBottom - marginTop)

    // Update over actors. Done in act() because actors may change position, which can fire enter/exit without an input event.
    for(pointer in pointerOverActors.indices){
      val overLast = pointerOverActors[pointer]
      // Check if pointer is gone.
      if(!pointerTouched[pointer]){
        if(overLast != null){
          pointerOverActors[pointer] = null
          screenToStageCoordinates(tempCoords.set(pointerScreenX[pointer].toFloat(), pointerScreenY[pointer].toFloat()))
          // Exit over last.
          val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
          event.type = InputEvent.InputEventType.exit
          event.stageX = tempCoords.x
          event.stageY = tempCoords.y
          event.relatedActor = overLast
          event.pointer = pointer
          overLast.fire(event)
          Pools.free(event)
        }
        continue
      }
      // Update over actor for the pointer.
      pointerOverActors[pointer] = fireEnterAndExit(overLast, pointerScreenX[pointer], pointerScreenY[pointer], pointer)
    }
    // Update over element for the mouse on the desktop.
    if(Core.app.isDesktop || Core.app.isWeb){
      mouseOverElement = fireEnterAndExit(mouseOverElement, mouseScreenX, mouseScreenY, -1)
    }else{
      mouseOverElement = hit(mouseScreenX.toFloat(), mouseScreenY.toFloat(), true)
    }

    val scroll = getScrollFocus()
    if(scroll != null && (!scroll.visible || scroll.scene == null)) scrollFocusElement = null
    val keyboard = getKeyboardFocus()
    if(keyboard != null && (!keyboard.visible || keyboard.scene == null)) keyFocus = null

    var curr = getScrollFocus()
    while(curr != null && curr.parent != null){
      if(!curr.visible){
        scrollFocusElement = null
        break
      }
      curr = curr.parent
    }

    root.act(delta)
  }

  override fun find(name: String): Element?{
    return root.find<Element>(name)
  }

  override fun findVisible(name: String): Element?{
    return root.findVisible<Element>(name)
  }

  override fun find(pred: Boolf<Element>): Element?{
    return root.find<Element>(pred)
  }

  override fun table(): Table{
    val table = Table()
    table.setFillParent(true)
    add(table)
    return table
  }

  override fun table(cons: Cons<Table>): Table{
    val table = Table()
    table.setFillParent(true)
    add(table)
    cons.get(table)
    return table
  }

  override fun table(style: Drawable?, cons: Cons<Table>): Table{
    val table = Table(style)
    table.setFillParent(true)
    add(table)
    cons.get(table)
    return table
  }

  private fun fireEnterAndExit(overLast: Element?, screenX: Int, screenY: Int, pointer: Int): Element?{
    // Find the actor under the point.
    screenToStageCoordinates(tempCoords.set(screenX.toFloat(), screenY.toFloat()))
    val over = hit(tempCoords.x, tempCoords.y, true)
    if(over === overLast) return overLast

    // Exit overLast.
    if(overLast != null){
      val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
      event.stageX = tempCoords.x
      event.stageY = tempCoords.y
      event.pointer = pointer
      event.type = InputEvent.InputEventType.exit
      event.relatedActor = over
      overLast.fire(event)
      Pools.free(event)
    }
    // Enter over.
    if(over != null){
      val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
      event.stageX = tempCoords.x
      event.stageY = tempCoords.y
      event.pointer = pointer
      event.type = InputEvent.InputEventType.enter
      event.relatedActor = overLast
      over.fire(event)
      Pools.free(event)
    }
    return over
  }

  override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: KeyCode): Boolean{
    if(!isInsideViewport(screenX, screenY)) return false

    pointerTouched[pointer] = true
    pointerScreenX[pointer] = screenX
    pointerScreenY[pointer] = screenY

    screenToStageCoordinates(tempCoords.set(screenX.toFloat(), screenY.toFloat()))

    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.touchDown
    event.stageX = tempCoords.x
    event.stageY = tempCoords.y
    event.pointer = pointer
    event.keyCode = button

    val target = hit(tempCoords.x, tempCoords.y, true)
    if(target == null){
      if(root.touchable == Touchable.enabled) root.fire(event)
    }else{
      target.fire(event)
    }

    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean{
    pointerScreenX[pointer] = screenX
    pointerScreenY[pointer] = screenY
    mouseScreenX = screenX
    mouseScreenY = screenY

    if(touchFocuses.size == 0) return false

    screenToStageCoordinates(tempCoords.set(screenX.toFloat(), screenY.toFloat()))

    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.touchDragged
    event.stageX = tempCoords.x
    event.stageY = tempCoords.y
    event.pointer = pointer

    val focuses = touchFocuses.begin()
    for(i in 0 until touchFocuses.size){
      val focus = focuses[i]
      if(focus.pointer != pointer) continue
      if(!touchFocuses.contains(focus, true)) continue // Touch focus already gone.
      event.targetActor = focus.target
      event.listenerActor = focus.listenerActor
      if(focus.listener?.handle(event) == true) event.handle()
    }
    touchFocuses.end()

    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: KeyCode): Boolean{
    pointerTouched[pointer] = false
    pointerScreenX[pointer] = screenX
    pointerScreenY[pointer] = screenY

    if(touchFocuses.size == 0) return false

    screenToStageCoordinates(tempCoords.set(screenX.toFloat(), screenY.toFloat()))

    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.touchUp
    event.stageX = tempCoords.x
    event.stageY = tempCoords.y
    event.pointer = pointer
    event.keyCode = button

    val focuses = touchFocuses.begin()
    for(i in 0 until touchFocuses.size){
      val focus = focuses[i]
      if(focus.pointer != pointer || focus.button != button) continue
      if(!touchFocuses.remove(focus, true)) continue // Touch focus already gone.
      event.targetActor = focus.target
      event.listenerActor = focus.listenerActor
      if(focus.listener?.handle(event) == true) event.handle()
      Pools.free(focus)
    }
    touchFocuses.end()

    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun mouseMoved(screenX: Int, screenY: Int): Boolean{
    if(!isInsideViewport(screenX, screenY)) return false

    mouseScreenX = screenX
    mouseScreenY = screenY

    screenToStageCoordinates(tempCoords.set(screenX.toFloat(), screenY.toFloat()))

    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.mouseMoved
    event.stageX = tempCoords.x
    event.stageY = tempCoords.y

    val target = hit(tempCoords.x, tempCoords.y, true) ?: root

    target.fire(event)
    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun scrolled(amountX: Float, amountY: Float): Boolean{
    val target = getScrollFocus() ?: root

    screenToStageCoordinates(tempCoords.set(mouseScreenX.toFloat(), mouseScreenY.toFloat()))

    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.scrolled
    event.scrollAmountX = amountX
    event.scrollAmountY = amountY
    event.stageX = tempCoords.x
    event.stageY = tempCoords.y
    target.fire(event)
    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun keyDown(keyCode: KeyCode): Boolean{
    val target = getKeyboardFocus() ?: root
    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.keyDown
    event.keyCode = keyCode
    target.fire(event)
    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun keyUp(keyCode: KeyCode): Boolean{
    val target = getKeyboardFocus() ?: root
    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.keyUp
    event.keyCode = keyCode
    target.fire(event)
    val handled = event.handled
    Pools.free(event)
    return handled
  }

  override fun keyTyped(character: Char): Boolean{
    val target = getKeyboardFocus() ?: root
    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.keyTyped
    event.character = character
    target.fire(event)
    val handled = event.handled
    Pools.free(event)
    return handled
  }

  /** Adds the listener to be notified for all touchDragged and touchUp events for the specified pointer and button. */
  override fun addTouchFocus(listener: EventListener, listenerActor: Element, target: Element, pointer: Int, button: KeyCode){
    val focus = Pools.obtain(TouchFocus::class.java) { TouchFocus() }
    focus.listenerActor = listenerActor
    focus.target = target
    focus.listener = listener
    focus.pointer = pointer
    focus.button = button
    touchFocuses.add(focus)
  }

  /**
   * Removes the listener from being notified for all touchDragged and touchUp events for the specified pointer and button. Note
   * the listener may never receive a touchUp event if this method is used.
   */
  override fun removeTouchFocus(listener: EventListener, listenerActor: Element, target: Element, pointer: Int, button: KeyCode){
    for(i in touchFocuses.size - 1 downTo 0){
      val focus = touchFocuses.get(i)
      if(focus.listener === listener && focus.listenerActor === listenerActor && focus.target === target
        && focus.pointer == pointer && focus.button == button){
        touchFocuses.remove(i)
        Pools.free(focus)
      }
    }
  }

  /** Cancels touch focus for the specified actor. */
  override fun cancelTouchFocus(actor: Element){
    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.touchUp
    event.stageX = Integer.MIN_VALUE.toFloat()
    event.stageY = Integer.MIN_VALUE.toFloat()

    // Cancel all current touch focuses for the specified listener, allowing for concurrent modification, and never cancel the
    // same focus twice.
    val items = touchFocuses.begin()
    for(i in 0 until touchFocuses.size){
      val focus = items[i]
      if(focus.listenerActor !== actor) continue
      if(!touchFocuses.remove(focus, true)) continue // Touch focus already gone.
      event.targetActor = focus.target
      event.listenerActor = focus.listenerActor
      event.pointer = focus.pointer
      event.keyCode = focus.button
      focus.listener?.handle(event)
      // Cannot return TouchFocus to pool, as it may still be in use (eg if cancelTouchFocus is called from touchDragged).
    }
    touchFocuses.end()

    Pools.free(event)
  }

  /**
   * Sends a touchUp event to all listeners that are registered to receive touchDragged and touchUp events and removes their
   * touch focus. This method removes all touch focus listeners, but sends a touchUp event so that the state of the listeners
   * remains consistent (listeners typically expect to receive touchUp eventually). The location of the touchUp is
   * Integer#MIN_VALUE. Listeners can use [InputEvent.isTouchFocusCancel] to ignore this event if needed.
   */
  override fun cancelTouchFocus(){
    cancelTouchFocusExcept(null, null)
  }

  /** Cancels touch focus for all listeners except the specified listener. */
  override fun cancelTouchFocusExcept(exceptListener: EventListener?, exceptActor: Element?){
    val event = Pools.obtain(InputEvent::class.java) { InputEvent() }
    event.type = InputEvent.InputEventType.touchUp
    event.stageX = Integer.MIN_VALUE.toFloat()
    event.stageY = Integer.MIN_VALUE.toFloat()

    // Cancel all current touch focuses except for the specified listener, allowing for concurrent modification, and never
    // cancel the same focus twice.
    val items = touchFocuses.begin()
    for(i in 0 until touchFocuses.size){
      val focus = items[i]
      if(focus.listener === exceptListener && focus.listenerActor === exceptActor) continue
      if(!touchFocuses.remove(focus, true)) continue // Touch focus already gone.
      event.targetActor = focus.target
      event.listenerActor = focus.listenerActor
      event.pointer = focus.pointer
      event.keyCode = focus.button
      focus.listener?.handle(event)
      // Cannot return TouchFocus to pool, as it may still be in use (eg if cancelTouchFocus is called from touchDragged).
    }
    touchFocuses.end()

    Pools.free(event)
  }

  /** Adds an actor to the root of the scene. */
  override fun add(actor: Element){
    root.addChild(actor)
  }

  /** Adds an action to the root of the scene. */
  override fun addAction(action: Action){
    root.addAction(action)
  }

  /** Returns the root's child elements. */
  override fun getElements(): Seq<Element>{
    return root.getChildren()
  }

  /** Adds a listener to the root. */
  override fun addListener(listener: EventListener): Boolean{
    return root.addListener(listener)
  }

  /** Removes a listener from the root. */
  override fun removeListener(listener: EventListener): Boolean{
    return root.removeListener(listener)
  }

  /** Adds a capture listener to the root. */
  override fun addCaptureListener(listener: EventListener): Boolean{
    return root.addCaptureListener(listener)
  }

  /** Removes a capture listener from the root. */
  override fun removeCaptureListener(listener: EventListener): Boolean{
    return root.removeCaptureListener(listener)
  }

  /** Removes the root's children, actions, and listeners. */
  override fun clear(){
    unfocusAll()
    root.clear()
  }

  /** Removes the touch, keyboard, and scroll focused actors. */
  override fun unfocusAll(){
    setScrollFocus(null)
    setKeyboardFocus(null)
    cancelTouchFocus()
  }

  /** Removes the touch, keyboard, and scroll focus for the specified actor and any descendants. */
  override fun unfocus(actor: Element){
    cancelTouchFocus(actor)
    val scroll = getScrollFocus()
    if(scroll != null && scroll.isDescendantOf(actor)) setScrollFocus(null)
    val keyboard = getKeyboardFocus()
    if(keyboard != null && keyboard.isDescendantOf(actor)) setKeyboardFocus(null)
  }

  override fun setKeyboardFocus(actor: Element?): Boolean{
    if(getKeyboardFocus() === actor) return true

    val event = Pools.obtain(FocusListener.FocusEvent::class.java) { FocusListener.FocusEvent() }
    event.type = FocusListener.FocusEvent.Type.keyboard
    val oldKeyboardFocus = getKeyboardFocus()
    if(oldKeyboardFocus != null){
      event.focused = false
      event.relatedActor = actor
      oldKeyboardFocus.fire(event)
    }
    var success = !event.cancelled
    if(success){
      keyFocus = actor
      if(actor != null){
        event.focused = true
        event.relatedActor = oldKeyboardFocus
        actor.fire(event)
        success = !event.cancelled
        if(!success) setKeyboardFocus(oldKeyboardFocus)
      }
    }
    Pools.free(event)
    return success
  }

  override fun getKeyboardFocus(): Element?{
    return keyFocus
  }

  override fun setScrollFocus(actor: Element?): Boolean{
    if(getScrollFocus() === actor) return true

    val event = Pools.obtain(FocusListener.FocusEvent::class.java) { FocusListener.FocusEvent() }
    event.type = FocusListener.FocusEvent.Type.scroll
    val oldScrollFocus = getScrollFocus()
    if(oldScrollFocus != null){
      event.focused = false
      event.relatedActor = actor
      oldScrollFocus.fire(event)
    }
    var success = !event.cancelled
    if(success){
      scrollFocusElement = actor
      if(actor != null){
        event.focused = true
        event.relatedActor = oldScrollFocus
        actor.fire(event)
        success = !event.cancelled
        if(!success) setScrollFocus(oldScrollFocus)
      }
    }
    Pools.free(event)
    return success
  }

  override fun getScrollFocus(): Element?{
    return scrollFocusElement
  }

  override fun getViewport(): Viewport{
    return viewportC
  }

  override fun setViewport(viewport: Viewport){
    viewportC = viewport
  }

  /** The viewport's world width. */
  override fun getWidth(): Float{
    return getViewport().worldWidth
  }

  /** The viewport's world height. */
  override fun getHeight(): Float{
    return getViewport().worldHeight
  }

  /** The viewport's camera. */
  override fun getCamera(): Camera{
    return getViewport().camera
  }

  /**
   * Returns the [Element] at the specified location in stage coordinates. Hit testing is performed in the order the actors
   * were inserted into the scene, last inserted actors being tested first. To get stage coordinates from screen coordinates,
   * use [screenToStageCoordinates].
   * @param touchable If true, the hit detection will respect the touchability.
   * @return May be null if no actor was hit.
   */
  override fun hit(stageX: Float, stageY: Float, touchable: Boolean): Element?{
    root.parentToLocalCoordinates(tempCoords.set(stageX, stageY))
    return root.hit(tempCoords.x, tempCoords.y, touchable)
  }

  /** Transforms the screen coordinates to stage coordinates. */
  override fun screenToStageCoordinates(screenCoords: Vec2): Vec2{
    getViewport().unproject(screenCoords)
    return screenCoords
  }

  /** Transforms the stage coordinates to screen coordinates. */
  override fun stageToScreenCoordinates(stageCoords: Vec2): Vec2{
    getViewport().project(stageCoords)
    stageCoords.y = getViewport().screenHeight - stageCoords.y
    return stageCoords
  }

  /**
   * Transforms the coordinates to screen coordinates. The coordinates can be anywhere in the scene since the transform matrix
   * describes how to convert them.
   */
  override fun toScreenCoordinates(coords: Vec2, transformMatrix: Mat): Vec2{
    return getViewport().toScreenCoordinates(coords, transformMatrix)
  }

  /** Calculates window scissor coordinates from local coordinates using the batch's current transformation matrix. */
  override fun calculateScissors(localRect: Rect, scissorRect: Rect){
    val transformMatrix = Draw.trans()
    getViewport().calculateScissors(transformMatrix, localRect, scissorRect)
  }

  override fun getActionsRequestRendering(): Boolean{
    return actionsRender
  }

  /**
   * If true, any actions executed during a call to [act] will result in a call to [Graphics.requestRendering].
   * Widgets that animate or otherwise require additional rendering may check this setting before calling
   * [Graphics.requestRendering]. Default is true.
   */
  override fun setActionsRequestRendering(actionsRequestRendering: Boolean){
    actionsRender = actionsRequestRendering
  }

  /** Check if screen coordinates are inside the viewport's screen area. */
  protected override fun isInsideViewport(screenX: Int, screenY: Int): Boolean{
    val x0 = getViewport().screenX
    val x1 = x0 + getViewport().screenWidth
    val y0 = getViewport().screenY
    val y1 = y0 + getViewport().screenHeight
    val sy = Core.graphics.height - screenY
    return screenX >= x0 && screenX < x1 && sy >= y0 && sy < y1
  }

  /** Updates the viewport. */
  override fun resize(width: Int, height: Int){
    getViewport().update(width, height, true)
  }

  /** Internal class for managing touch focus. */
  private class TouchFocus : Poolable{
    var listener: EventListener? = null
    var listenerActor: Element? = null
    var target: Element? = null
    var pointer = 0
    var button: KeyCode? = null

    override fun reset(){
      listenerActor = null
      listener = null
      target = null
    }
  }
}
