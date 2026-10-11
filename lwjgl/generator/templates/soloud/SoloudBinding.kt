package soloud

import org.lwjgl.generator.*

val SOLOUD_BINDING =
  simpleBinding(
    PeroxicoreModules.soloud,
    libraryExpression = """Platform.mapLibraryNameBundled("soloud")""",
    bundledWithLWJGL = true,
  )

val SOLOUD_BINDING_DELEGATE = SOLOUD_BINDING.delegate("SoLoud.getLibrary()")

fun String.nativeClassSDL(
  templateName: String,
  prefix: String = "",
  init: (NativeClass.() -> Unit)? = null,
) =
  nativeClass(
    PeroxicoreModules.soloud,
    templateName,
    prefixMethod = prefix,
    binding = SOLOUD_BINDING_DELEGATE,
    init = init,
  )
