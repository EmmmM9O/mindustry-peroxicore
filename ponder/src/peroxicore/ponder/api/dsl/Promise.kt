@file:Suppress("UNCHECKED_CAST")

package peroxicore.ponder.api.dsl

class Promise<R : Any, T> {
  var done = false
  var value: T? = null
  lateinit var receiver: R
  var callback: (R.(T?) -> Unit)? = null

  inline fun <Res> thenChain(crossinline next: R.(T?) -> Promise<R, Res>): Promise<R, Res> {
    if (done) {
      return receiver.next(value)
    } else {
      val promise = Promise<R, Res>()
      callback = { value ->
        next(value).let { pro ->
          promise.callback?.let {
            pro.then(it)
          }
        }
      }
      return promise
    }
  }

  inline fun <Res> thenPromise(crossinline next: R.(T?) -> Res): Promise<R, Res> {
    val promise = Promise<R, Res>()
    callback = { value ->
      promise.resolve(this, next(value))
    }
    if (done) callback?.invoke(receiver, value)
    return promise
  }

  inline fun then(crossinline next: R.(T?) -> Unit) {
    callback = { value ->
      next(value)
    }
    if (done) callback?.invoke(receiver, value)
  }

  inline fun <P> thenAs(crossinline next: R.(P) -> Unit) {
    callback = { value ->
      (value as? P)?.let {
        next(it)
      }
    }
    if (done) callback?.invoke(receiver, value)
  }

  fun resolve(
    receiver: R,
    value: T,
  ) {
    callback?.invoke(receiver, value)
    done = true
    this.value = value
    this.receiver = receiver
  }
}
