package peroxicore.ponder.api.ciallo

import arc.files.*
import arc.util.*
import mindustry.ctype.*
import peroxicore.ciallo.*
import peroxicore.ciallo.value.*
import peroxicore.ponder.api.*
import peroxicore.ponder.scene.*

/**
 * Entry point for Ponder scenes written in Ciallo.
 *
 * A script names the content it documents and gives a body:
 *
 * ```
 * @Blocks.itemSource scene {
 *   tag "blocks"
 *   duration 8s
 *   world(11, 11) {
 *     fillFloor @Blocks.stone
 *   }
 *
 *   frame(1s) {
 *     build @Blocks.itemSource at(2, 5)
 *   }
 *   frame(2s) {
 *     at(5, 5) showConfig()
 *   }
 * }
 * ```
 *
 * `scene` runs the body once — timeline settings apply and world actions are
 * recorded — then builds and registers a normal
 * [PonderScene][peroxicore.ponder.scene.PonderScene].  The `world`/`frame` bodies
 * and recorded actions replay against the live Ponder timeline, so a script and
 * the Kotlin DSL share one lifecycle.
 *
 * Use [environment] (or call [install]) to get a [CialloEnvironment] where
 * `@Blocks`, `@Items`, ... resolve; [run] and [loadScripts] execute scripts
 * against it.
 */
object CialloPonder {
  private val installed = linkedSetOf<CialloEnvironment>()

  /** The shared environment used by [run] and [loadScripts]; created on first use. */
  val environment: CialloEnvironment by lazy {
    CialloEnvironment(output = { Log.info("[Ciallo] $it") }).also(::install)
  }

  /** Teaches [environment] about Ponder. Idempotent. */
  fun install(environment: CialloEnvironment) {
    if (!installed.add(environment)) return
    environment.operation(CialloPonderResolver())
  }

  /** Runs [source] against [environment], registering every scene it defines. */
  fun run(
    source: String,
    sourceName: String = "<ciallo>",
    environment: CialloEnvironment = this.environment,
  ): CialloValue {
    install(environment)
    return CialloEngine(environment).execute(source, sourceName)
  }

  /** Runs a `.ciallo` file against [environment]. */
  fun runFile(
    file: Fi,
    environment: CialloEnvironment = this.environment,
  ): CialloValue = run(file.readString(), file.name(), environment)

  /**
   * Runs every `*.ciallo` file directly inside [directory], in name order.
   * A missing directory is ignored, so callers need no existence check.
   */
  fun loadScripts(
    directory: Fi,
    environment: CialloEnvironment = this.environment,
  ): Int {
    if (!directory.exists() || !directory.isDirectory) return 0
    var count = 0
    directory
      .list()
      .filter { it.extension().equals("ciallo", ignoreCase = true) }
      .sortedBy { it.name() }
      .forEach { file ->
        runFile(file, environment)
        count++
      }
    return count
  }

  /**
   * Runs [body] with [content] as the scene target and returns the built scene.
   * Registration is up to the caller; [register] does both.
   */
  fun scene(
    environment: CialloEnvironment,
    content: UnlockableContent,
    body: CialloBlockValue,
  ): UnlockableContentPonder {
    install(environment)
    val scope = CialloSceneScope(content, environment)
    scope.define(body)
    return scope.build()
  }

  /** Runs [body] and registers the scene in [PonderRegistry]. */
  fun register(
    environment: CialloEnvironment,
    content: UnlockableContent,
    body: CialloBlockValue,
  ): UnlockableContentPonder {
    val scene = scene(environment, content, body)
    PonderRegistry.contentScenes.put(content, scene)
    return scene
  }
}
