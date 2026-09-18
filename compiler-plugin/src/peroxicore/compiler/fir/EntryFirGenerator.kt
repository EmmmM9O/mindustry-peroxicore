package peroxicore.complier.fir

import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.java.*
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.resolve.providers.*
import org.jetbrains.kotlin.fir.symbols.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.jvm.*
import org.jetbrains.kotlin.name.*
import peroxicore.complier.*

@OptIn(
  SymbolInternals::class,
  UnresolvedExpressionTypeAccess::class,
  DirectDeclarationsAccess::class,
)
class EntryFirGenerator(
  session: FirSession,
  val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    fun factory(messageCollector: MessageCollector) = Factory { session ->
      EntryFirGenerator(session, messageCollector)
    }
  }

  fun processAll() =
    AnnoMarker.caches
      .map { (anno, symbols) ->
        anno.annotationClass to
          symbols.map { classSymbol ->
            classSymbol.classId.asSingleFqName() to
              anno.annotateds
                .filter {
                  it.dispatchReceiverType!!.classId == classSymbol.classId
                }
                .map { it to it.getAnnotationByClassId(anno.annotationClass, session)!! }
          }
      }
      .toMap()

  // MutableMap<ClassId, MutableList<Pair<FqName, MutableList<>>>

  private val predicateBasedProvider = session.predicateBasedProvider

  fun FirTypeRef.asCone() =
    when (this) {
      is FirResolvedTypeRef -> coneType
      is FirJavaTypeRef ->
        (resolveIfJavaType(session, JavaTypeParameterStack.EMPTY, null) as FirResolvedTypeRef)
          .coneType
      else -> throw RuntimeException("Unknown typeref $this")
    }

  fun ConeClassLikeType.toFir() =
    session.symbolProvider.getClassLikeSymbolByClassId(classId)?.fir as? FirClass

  fun ConeClassLikeType.isClass() =
    toFir()?.classKind?.run {
      isClass && !isInterface
    } ?: false

  fun FirClass.parent() =
    superTypeRefs
      .asSequence()
      .filterIsInstance<FirResolvedTypeRef>()
      .map { it.coneType }
      .filterIsInstance<ConeClassLikeType>()
      .firstOrNull { it.isClass() }
      ?.toFir()

  fun FirClass.ancestors(): List<FirClass> =
    parent()?.let { listOf(it, *it.ancestors().toTypedArray()) } ?: emptyList()

  val parentsFuncs = mutableMapOf<FqName, Map<Name, MutableList<FirNamedFunctionSymbol>>>()

  fun FirFunction.isSui(): Boolean {
    val status = this.status
    val modality = status.modality
    val visibility = status.visibility
    return visibility != Visibilities.Private && modality == Modality.OPEN
  }

  fun fillFatherFuncs(
    map: MutableMap<Name, MutableList<FirNamedFunctionSymbol>>,
    target: FirClass,
  ) {
    target.symbol.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().forEach {
      if (it.fir.isSui()) {
        val funcs = map.getOrPut(it.name) { mutableListOf() }
        val stype = it.typeStr()
        if (!funcs.any { f -> f.typeStr() == stype }) funcs.add(it)
      }
    }
    target.parent()?.let { fillFatherFuncs(map, it) }
  }

  fun ancestorsOf(fir: FirClass): List<FirClass> =
    fir.superTypeRefs
      .asSequence()
      .filterIsInstance<FirResolvedTypeRef>()
      .map { it.coneType }
      .filterIsInstance<ConeClassLikeType>()
      .mapNotNull { it.toFir() }
      .filter { it.classKind.isInterface }
      .flatMap { listOf(it, *ancestorsOf(it).toTypedArray()) }
      .toList()

  fun FirNamedFunction.typeStr() = valueParameters.typeStr()

  fun FirNamedFunctionSymbol.typeStr() = fir.typeStr()

  fun List<FirValueParameter>.typeStr() =
    joinToString(",") {
      it.returnTypeRef.asCone().classId!!.asSingleFqName().asString()
    }

  fun List<FirNamedFunctionSymbol>.possible(method: String) = filter {
    it.name.asString() == method
  }

  fun List<FirNamedFunctionSymbol>.findFunction(
    method: String,
    type: String,
    origin: FirNamedFunctionSymbol,
  ): FirNamedFunctionSymbol? {
    val candidates = possible(method)
    if (candidates.isEmpty()) return null

    if (type.isEmpty()) {
      if (candidates.size == 1) return candidates.first()
      // 寻找参数类型一样的
      val ptypes = origin.typeStr()
      return candidates.firstOrNull { func -> func.fir.typeStr() == ptypes }
    }
    return candidates.firstOrNull { it.typeStr() == type }
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val fir = classSymbol.fir
    if (fir !is FirRegularClass || !fir.classKind.isClass || fir.classKind.isInterface) {
      return emptySet()
    }
    val parents =
      fir.superTypeRefs
        .filterIsInstance<FirResolvedTypeRef>()
        .map { it.coneType }
        .filterIsInstance<ConeClassLikeType>()

    val ancestors = ancestorsOf(fir)
    val comps = ancestors.map { it.symbol.classId.asSingleFqName() }
    val fqName = classSymbol.classId.asSingleFqName()
    val all = processAll()
    if (!all.any { (_, symbols) -> symbols.any { (name, _) -> comps.contains(name) } }) {
      return emptySet()
    }

    val parent = parents.firstOrNull { it.isClass() }
    val pf = mutableMapOf<Name, MutableList<FirNamedFunctionSymbol>>()
    parent?.toFir()?.let { fillFatherFuncs(pf, it) }

    parentsFuncs[fqName] = pf

    val result = mutableSetOf<Name>()
    val tname = fqName.asString()
    info("@ImplEntries Process $tname")
    val fcomps = mutableListOf<String>()
    all.forEach { (anno, symbols) ->
      symbols.forEach { (name, functions) ->
        if (comps.contains(name)) {
          fcomps.add("`${name.asString()}`")
          functions.forEach {
            entryProcessors[anno]?.process(name, session, messageCollector, classSymbol, result, it)
          }
        }
      }
    }
    info("Found components for `$tname`: ${fcomps.joinToString()}")
    rewriteList
      .takeIf { it.isNotEmpty() }
      ?.let { info("Rewrite functions in `$tname`: ${it.joinToString()}") }
    defineList
      .takeIf { it.isNotEmpty() }
      ?.let { info("Define functions in `$tname`: ${it.joinToString()}") }
    rewriteList.clear()
    defineList.clear()
    return result
  }

  fun info(text: String) {
    // 我没有其他办法输出信息了
    messageCollector.report(
      CompilerMessageSeverity.WARNING,
      "[PREOXIDE-INFO]: $text",
    )
    System.err.println("[INFO]: $text")
  }

  fun interface EntryProcessor {
    fun process(
      origin: FqName,
      session: FirSession,
      messageCollector: MessageCollector,
      classSymbol: FirClassSymbol<*>,
      result: MutableSet<Name>,
      function: Pair<FirNamedFunctionSymbol, FirAnnotation>,
    )
  }

  fun FirAnnotationCall.firstA(): String? {
    if (arguments.isEmpty()) return null
    val first = arguments.first()
    if (first is FirLiteralExpression) return first.value as? String
    arguments.filterIsInstance<FirNamedArgumentExpression>().forEach {
      val exp = it.expression
      if (exp is FirLiteralExpression) return exp.value as? String
    }
    return null
  }

  fun splitPair(input: String): Pair<String, String> {
    val idx = input.indexOf(':')
    return if (idx == -1) input to "" else input.substring(0, idx) to input.substring(idx + 1)
  }

  val rewriteList = mutableSetOf<String>()
  val defineList = mutableSetOf<String>()

  fun String.addK() = takeIf { it.isNotEmpty() }?.let { "($it)" } ?: ""

  fun FirNamedFunctionSymbol.printString() =
    "${dispatchReceiverType!!.classId!!.asString()}.${name.asString()}"

  val funcsToDefine = mutableMapOf<Pair<FqName, Name>, MutableSet<FirNamedFunctionSymbol>>()

  val entryProcessors =
    mutableMapOf<ClassId, EntryProcessor>(
      Annotations.MethodEntry to
        EntryProcessor { rorigin, session, messageCollector, classSymbol, result, function ->
          val (origin, annotation) = function
          ((annotation as? FirAnnotationCall)?.firstA())?.also { target ->
            val (name, types) = splitPair(target)
            val rname = Name.identifier(name)
            val fqName = classSymbol.classId.asSingleFqName()
            val thisFuncs =
              classSymbol.declarationSymbols
                .filterIsInstance<FirNamedFunctionSymbol>()
                .possible(name)
            val thisFuncsTypes = thisFuncs.map { it.typeStr() }
            val parentFuncs =
              parentsFuncs[fqName]?.get(rname)?.filter { it.typeStr() !in thisFuncsTypes }
                ?: emptyList()
            val allFuncs = mutableListOf<FirNamedFunctionSymbol>()
            allFuncs.addAll(thisFuncs)
            allFuncs.addAll(parentFuncs)

            val func =
              allFuncs.findFunction(name, types, origin)
                ?: run {
                  messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "@MethodEntry can not find suitable `$name${types.addK()}` in `${fqName.asString()}` for `${origin.printString()}`.",
                  )
                  allFuncs
                    .takeIf { it.isNotEmpty() }
                    ?.let { possibleList ->
                      messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "Possible candidates: ${possibleList.map{"${it.printString()} Types:`${it.typeStr()}`"}}",
                      )
                    }
                  return@also
                }

            if (thisFuncs.contains(func)) {
              rewriteList.add("`$name(${func.typeStr()})`")
            } else {
              val types = func.typeStr()
              val resName = Name.identifier(name)
              result.add(resName)
              funcsToDefine.getOrPut(fqName to resName) { mutableSetOf() }.add(func)
              defineList.add("`$name($types)`")
            }
          }
            ?: run {
              messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "@MethodEntry without arguement",
              )
            }
        }
    )

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val classSymbol = context?.owner ?: return emptyList()
    val scope = context.declaredScope ?: return emptyList()

    val fqName = classSymbol.classId.asSingleFqName()
    val name = callableId.callableName
    return funcsToDefine[fqName to name]!!
      .map {
        val func = it.fir
        createMemberFunction(
            classSymbol,
            PluginKeys.methodEntry,
            name,
            func.returnTypeRef.asCone(),
          ) {
            func.valueParameters.forEach { p ->
              valueParameter(p.name, p.returnTypeRef.asCone())
            }
            modality = Modality.OPEN
          }
          .symbol
      }
      .toList()

    /*
    val res =       copyFirFunctionWithResolvePhase(
          function,
          callableId,
          PluginKeys.methodEntry,
          FirResolvePhase.STATUS,
        ) {}*/
  }
}

@OptIn(SymbolInternals::class)
open class AnnoMarker(
  session: FirSession,
  val annotationClass: ClassId,
  val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    fun factory(annotation: ClassId, messageCollector: MessageCollector) = Factory { session ->
      AnnoMarker(session, annotation, messageCollector)
    }

    val caches = mutableMapOf<AnnoMarker, MutableList<FirClassSymbol<*>>>()
  }

  val annotation = annotationClass.asSingleFqName()

  private val predicateBasedProvider = session.predicateBasedProvider

  val predicate = DeclarationPredicate.create {
    hasAnnotated(annotation)
  }

  val lookup = LookupPredicate.create {
    annotated(annotation)
  }

  val annotateds by lazy {
    predicateBasedProvider
      .getSymbolsByPredicate(lookup)
      .filterIsInstance<FirNamedFunctionSymbol>()
      .filter { it.dispatchReceiverType is ConeClassLikeType }
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val fir = classSymbol.fir
    if (!predicateBasedProvider.matches(predicate, fir)) {
      return emptySet()
    }
    if (fir !is FirRegularClass) {
      messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "@MethodEntry found in ${classSymbol}. But it is for class only",
      )
      return emptySet()
    }
    caches.getOrPut(this) { mutableListOf() }.add(classSymbol)
    return emptySet()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(predicate)
  }
}
