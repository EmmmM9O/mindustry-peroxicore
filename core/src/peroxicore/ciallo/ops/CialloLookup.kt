package peroxicore.ciallo.ops

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/** Whether a member handler answers `receiver.name`; a handler that answers everything is always consulted. */
fun CialloMemberHandler.handles(receiver: CialloValue, name: String): Boolean =
  (this as? CialloMemberPredicate)?.accepts(receiver, name) ?: true

/** Optional: lets a member handler decline requests it does not understand. */
fun interface CialloMemberPredicate {
  fun accepts(receiver: CialloValue, name: String): Boolean
}

/** The member table a value advertises, following [CialloValue.memberTarget]. */
fun CialloValue.memberTableView(): CialloMemberTable? = members.tableFor(this)
