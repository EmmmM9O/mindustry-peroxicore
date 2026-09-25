package peroxicore.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.symbols.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.*
import peroxicore.compiler.*

@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
class ActionFirGenerator(
  session: FirSession,
  val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    fun factory(messageCollector: MessageCollector) =
      Factory { session ->
        ActionFirGenerator(session, messageCollector)
      }
  }

  fun FirDeclaration.name() =
    when (this) {
      is FirRegularClass -> name.asString()
      is FirTypeAlias -> name.asString()
      else -> ""
    }

  fun FirTypeRef.name() =
    when (this) {
      is FirUserTypeRef -> {
        this.shortName.asString()
      }

      is FirResolvedTypeRef -> {
        this.coneType.classId
          ?.shortClassName
          ?.asString() ?: ""
      }

      else -> {
        ""
      }
    }

  fun FirDeclaration.poHas(name: ClassId) =
    annotations.any {
      it.annotationTypeRef.name() == name.shortClassName.asString()
    }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> =
    classSymbol.fir.declarations
      .filter {
        it.poHas(Annotations.Actionable)
      }.map {
        info("find actionable ${it.name()}")
        Name.identifier("${it.name()}Action")
      }.toSet()

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    val targetName = name.asString().removeSuffix("Action")
    val targetDecl =
      owner.fir.declarations
        .firstOrNull {
          it.name() == targetName &&
            it.poHas(
              Annotations.Actionable
            )
        }
        ?: return null
    val targetClassSymbol =
      when (targetDecl) {
        is FirRegularClass -> targetDecl.symbol
        is FirTypeAlias -> targetDecl.expandedTypeRef
        else -> return null
      }
    return createNestedClass(owner, name, PluginKeys.actionable, ClassKind.CLASS) {
      modality = Modality.FINAL
      typeParameter(AnnoProps.resType)
    }.symbol
  }

  fun info(text: String) {
    // 我没有其他办法输出信息了
    messageCollector.report(
      CompilerMessageSeverity.WARNING,
      "[PREOXIDE-INFO]: $text"
    )
  }
}
