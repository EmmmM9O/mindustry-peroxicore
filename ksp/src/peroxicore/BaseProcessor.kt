package peroxicore

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

abstract class BaseProcessor(
  val codeGenerator: CodeGenerator,
  val logger: KSPLogger,
  val options: Map<String, String>,
) : SymbolProcessor {
  val logTimes: Boolean by lazy {
    options.getOrDefault(KspOptions.logTimes, "true") == true.toString()
  }
  val logConfigs: Boolean by lazy {
    options.getOrDefault(KspOptions.logConfigs, "true") == true.toString()
  }

  inline fun <reified T> Resolver.symbols(isValid: Boolean) =
    getSymbolsWithAnnotation(T::class.qualifiedName!!).partition { it.validate() || !isValid }

  inline fun <reified T> Resolver.annotations() =
    symbols<T>(false).first.map {
      it.annotation<T>()
        ?: fail("Cannot find ${T::class.qualifiedName}.But in theory, it should exist.", it)
    }

  inline fun <reified T> Resolver.annotationPairs() =
    symbols<T>(false).first.map {
      it to
        (
          it.annotationNotNull<T>()
        )
    }

  fun KSAnnotated.annotationName(qualifiedName: String) =
    annotations.find {
      it.annotationType
        .resolve()
        .declaration.qualifiedName
        ?.asString() == qualifiedName
    }

  inline fun <reified T> KSAnnotated.annotation() = annotationName(T::class.qualifiedName!!)

  inline fun <reified T> KSAnnotated.annotationNotNull() =
    annotation<T>()
      ?: fail("Cannot find ${T::class.qualifiedName}.But in theory, it should exist.", this)

  fun KSAnnotated.locate() = location.toString().let { " at $it" }

  fun fail(
    message: String,
    symbol: KSAnnotated? = null,
  ): Nothing {
    val location = symbol?.locate() ?: ""
    val fullMessage = "$message$location"
    logger.error(fullMessage)
    throw RuntimeException(fullMessage)
  }

  fun info(message: String) {
    println("[PEROXICORE-INFO]: $message")
  }
}
