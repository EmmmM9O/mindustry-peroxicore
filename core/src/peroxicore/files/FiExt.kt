package peroxicore.files

import arc.files.*

fun Fi.sub(path: String): Fi? {
  var res = this
  path.split("/").forEach {
    res = res.child(it)
  }
  return res
}
