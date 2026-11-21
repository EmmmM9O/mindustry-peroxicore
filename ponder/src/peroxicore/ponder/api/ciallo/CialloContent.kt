package peroxicore.ponder.api.ciallo

import arc.util.*
import mindustry.*
import mindustry.ctype.*
import mindustry.type.*
import mindustry.world.*
import peroxicore.ciallo.*
import peroxicore.ciallo.value.*
/**
 * One content entry, e.g. the object `@Blocks.copperWall` resolves to.
 *
 * It is a [CialloHostObject], so it flows into host parameters typed as
 * `mindustry.world.Block` / `mindustry.type.Item` without reflection.
 */
class CialloContentValue(
  val content: Content,
) : CialloHostObject {
  override val host: Any get() = content

  override val kind: CialloKind get() = CialloKind.Obj

  override val members: CialloMembers get() = ContentMembers

  override fun display(): String = (content as? MappableContent)?.name ?: content.toString()

  override fun equals(other: Any?): Boolean = other is CialloContentValue && other.content === content

  override fun hashCode(): Int = System.identityHashCode(content)
}

private val contentTable: CialloMemberTable = memberTable {
  property("name") { _, value ->
    CialloStr((value as CialloContentValue).content.let { (it as? MappableContent)?.name ?: "" })
  }

  property("id") { _, value -> CialloInt((value as CialloContentValue).content.id.toInt()) }

  property("localizedName") { _, value ->
    val content = (value as CialloContentValue).content
    CialloStr((content as? UnlockableContent)?.localizedName ?: (content as? MappableContent)?.name ?: "")
  }

  property("type") { _, value ->
    CialloStr((value as CialloContentValue).content.getContentType().name)
  }

  property("block") { _, value -> CialloBool((value as CialloContentValue).content is Block) }

  property("item") { _, value -> CialloBool((value as CialloContentValue).content is Item) }

  property("unit") { _, value -> CialloBool((value as CialloContentValue).content is UnitType) }

  property("liquid") { _, value -> CialloBool((value as CialloContentValue).content is Liquid) }

  property("size") { _, value ->
    val block = (value as CialloContentValue).content as? Block
    if (block == null) CialloNil.INSTANCE else CialloInt(block.size)
  }

  property("solid") { _, value ->
    val block = (value as CialloContentValue).content as? Block
    if (block == null) CialloNil.INSTANCE else CialloBool(block.solid)
  }

  property("health") { _, value ->
    when (val content = (value as CialloContentValue).content) {
      is Block -> CialloInt(content.health.toInt())
      is UnitType -> CialloInt(content.health.toInt())
      else -> CialloNil.INSTANCE
    }
  }

  method("toString") { _, value, _ -> CialloStr(value.render()) }

  /**
   * `@Blocks.itemSource scene { ... }` — defines and registers a Ponder scene for
   * this content, so `PonderRegistry.show(itemSource)` can open it right away.
   */
  method("scene") { context, value, args ->
    val content =
      (value as CialloContentValue).content as? UnlockableContent
        ?: throw CialloRuntimeException("scene needs unlockable content, got ${value.display()}")
    val block =
      args.lastOrNull() as? CialloBlockValue
        ?: throw CialloRuntimeException("scene expects a block, as in 'scene { ... }'")
    CialloPonder.register(context.environment, content, block)
    CialloNil.INSTANCE
  }
}

/** Members of every [CialloContentValue]. */
object ContentMembers : CialloMembers by contentTable

/**
 * A content family such as `Blocks` or `Items`, resolved lazily so scripts may
 * name content before or after the loader publishes it.
 *
 * A member read maps the camelCase spelling to Mindustry's kebab-case names, so
 * `@Blocks.copperWall` finds `copper-wall`.  `get`, `getById`, `has` and `all`
 * cover the remaining lookups.
 */
class CialloContentNamespace(
  val type: ContentType,
  private val displayName: String,
) : CialloValue {
  override val kind: CialloKind get() = CialloKind.Obj

  override val members: CialloMembers get() = ContentNamespaceMembers

  override fun display(): String = displayName

  fun byName(name: String): Content? {
    @Suppress("UNCHECKED_CAST")
    val direct = Vars.content.getByName(type, name) as Content?
    if (direct != null) return direct
    @Suppress("UNCHECKED_CAST")
    val kebab = Vars.content.getByName(type, Strings.camelToKebab(name)) as Content?
    if (kebab != null) return kebab
    @Suppress("UNCHECKED_CAST")
    return Vars.content.getByName(type, name.replace('_', '-')) as Content?
  }

  fun byId(id: Int): Content? {
    @Suppress("UNCHECKED_CAST")
    return Vars.content.getByID(type, id) as Content?
  }

  fun all(): List<Content> = Vars.content.getBy<Content>(type).toList()

  override fun toString(): String = displayName
}

private val namespaceTable: CialloMemberTable = memberTable {
  memberFallback { _, value, name ->
    (value as? CialloContentNamespace)?.byName(name)?.let { CialloContentValue(it) }
  }

  method("get") { context, value, args ->
    requireArgs("get", args, 1)
    val namespace = value as CialloContentNamespace
    when (val key = context.resolve(args[0])) {
      is CialloInt -> namespace.byId(key.value)?.let { CialloContentValue(it) } ?: CialloNil.INSTANCE
      is CialloStr -> namespace.byName(key.value)?.let { CialloContentValue(it) } ?: CialloNil.INSTANCE
      else -> throw CialloRuntimeException("get expects an Int id or a String name")
    }
  }

  method("getById") { context, value, args ->
    requireArgs("getById", args, 1)
    val namespace = value as CialloContentNamespace
    namespace.byId(context.int(args[0], "getById"))?.let { CialloContentValue(it) } ?: CialloNil.INSTANCE
  }

  method("has") { context, value, args ->
    requireArgs("has", args, 1)
    val namespace = value as CialloContentNamespace
    CialloBool(namespace.byName(context.resolve(args[0]).render()) != null)
  }

  property("all") { _, value ->
    CialloArr((value as CialloContentNamespace).all().mapTo(mutableListOf()) { CialloContentValue(it) })
  }

  property("size") { _, value -> CialloInt((value as CialloContentNamespace).all().size) }

  method("toString") { _, value, _ -> CialloStr(value.render()) }
}

/** Members of [CialloContentNamespace]. */
object ContentNamespaceMembers : CialloMembers by namespaceTable
