package peroxicore.ponder.api.ciallo

import arc.util.*
import mindustry.*
import mindustry.ctype.*
import mindustry.game.*
import peroxicore.ciallo.*
import peroxicore.ciallo.host.*
import peroxicore.ciallo.value.*

/**
 * Ciallo access to Mindustry's content registries.
 *
 * `@Blocks` is a [CialloContentNamespace]; `@Blocks.copperWall` resolves
 * Mindustry's `copper-wall` from the camelCase spelling, while
 * `@Blocks.getById(5)` and `@Blocks.all` cover explicit lookups.  `@Teams` is
 * separate because [Team] is not [Content][mindustry.ctype.Content].
 */
fun contentNamespaces(): Map<String, CialloValue> =
  linkedMapOf(
    "Blocks" to CialloContentNamespace(ContentType.block, "Blocks"),
    "Items" to CialloContentNamespace(ContentType.item, "Items"),
    "Liquids" to CialloContentNamespace(ContentType.liquid, "Liquids"),
    "Units" to CialloContentNamespace(ContentType.unit, "Units"),
    "StatusEffects" to CialloContentNamespace(ContentType.status, "StatusEffects"),
    "Bullets" to CialloContentNamespace(ContentType.bullet, "Bullets"),
    "Planets" to CialloContentNamespace(ContentType.planet, "Planets"),
    "Sectors" to CialloContentNamespace(ContentType.sector, "Sectors"),
    "Weathers" to CialloContentNamespace(ContentType.weather, "Weathers"),
    "Teams" to CialloTeamNamespace,
  )

/** `@Teams`, resolving [Team] by name (`@Teams.crux`) or id (`@Teams.get(2)`). */
object CialloTeamNamespace : CialloValue {
  override val kind: CialloKind get() = CialloKind.Obj

  override val members: CialloMembers get() = CialloTeamMembers

  override fun display(): String = "Teams"

  fun byName(name: String): Team? = Team.all.firstOrNull { it != null && it.name == name }

  fun byId(id: Int): Team = Team.get(id)

  fun all(): List<Team> = Team.all.filterNotNull()
}

private val teamTable: CialloMemberTable = memberTable {
  memberFallback { _, value, name ->
    (value as? CialloTeamNamespace)?.byName(name)?.let { CialloHostValues.of(it) }
  }

  method("get") { context, value, args ->
    requireArgs("get", args, 1)
    CialloHostValues.of((value as CialloTeamNamespace).byId(context.int(args[0], "get")))
  }

  method("getById") { context, value, args ->
    requireArgs("getById", args, 1)
    CialloHostValues.of((value as CialloTeamNamespace).byId(context.int(args[0], "getById")))
  }

  property("all") { _, value ->
    CialloArr((value as CialloTeamNamespace).all().mapTo(mutableListOf()) { CialloHostValues.of(it) })
  }

  method("toString") { _, value, _ -> CialloStr(value.render()) }
}

/** Members of [CialloTeamNamespace]. */
object CialloTeamMembers : CialloMembers by teamTable
