package peroxicore.ciallo

import java.io.*

/**
 * Host-side manager for multiple isolated Ciallo global environments.
 *
 * Each environment owns its [CialloEnvironment.globalScope], registered symbols,
 * natives, scene and [CialloSandbox].  Environments created here are independent:
 * mounting a variable in one does not affect another, and a safe environment and
 * a full-access environment can coexist.
 */
class CialloRuntime(
  private val defaultSandbox: CialloSandbox = CialloSandbox.SAFE,
  private val defaultOutput: (String) -> Unit = { println(it) },
) {
  private val environments = linkedMapOf<String, CialloEnvironment>()

  fun create(
    name: String,
    sandbox: CialloSandbox = defaultSandbox,
    output: (String) -> Unit = defaultOutput,
  ): CialloEnvironment {
    if (environments.containsKey(name)) {
      throw CialloRuntimeException("environment '$name' already exists")
    }
    val environment = CialloEnvironment(output, sandbox)
    environments[name] = environment
    return environment
  }

  fun environment(name: String): CialloEnvironment =
    environments[name] ?: throw CialloRuntimeException("unknown environment '$name'")

  fun engine(name: String): CialloEngine = CialloEngine(environment(name))

  fun execute(name: String, source: String, sourceName: String = name): CialloValue =
    engine(name).execute(source, sourceName)

  fun executeFile(name: String, file: File): CialloValue =
    engine(name).executeFile(file)

  fun remove(name: String): Boolean = environments.remove(name) != null

  fun names(): Set<String> = environments.keys.toSet()

  fun clear() {
    environments.clear()
  }
}
