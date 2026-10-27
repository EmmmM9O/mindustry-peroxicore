package peroxicore

import com.google.auto.service.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.palantir.javapoet.*
import javax.lang.model.element.Modifier

/**
 * Generic template API generator.
 *
 * A template is a normal Kotlin interface annotated with [peroxicore.annotations.ApiTemplate]. KSP
 * reads only its symbols (never its source text) and generates:
 *
 * - a public Kotlin interface in the requested package which extends the template and adds action
 *   type aliases plus action-lambda overloads;
 * - optionally, a Java facade with fluent "at(x, y).build(...)" style handles.
 *
 * Keeping the templates as compiled interfaces means default implementations, overloads and
 * parameter types stay the single source of truth.
 */
class TemplateApiProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
) : SymbolProcessor {

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val templates =
      resolver
        .getSymbolsWithAnnotation(Annotations.ApiTemplate)
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.INTERFACE }
        .toList()
    if (templates.isEmpty()) return emptyList()

    val names =
      templates
        .associate {
          val fqn = it.qualifiedName?.asString() ?: return@associate "" to ""
          fqn to generatedFqn(it)
        }
        .filterKeys { it.isNotEmpty() }

    templates.forEach { template ->
      try {
        generateKotlin(template)
      } catch (e: Exception) {
        logger.error(
          "Failed to generate Kotlin API for ${template.simpleName.asString()}: ${e.message}",
          template,
        )
      }
    }
    templates.forEach { template ->
      if (stringArg(template, "javaName").isEmpty()) return@forEach
      try {
        generateJava(template, names)
      } catch (e: Exception) {
        logger.error(
          "Failed to generate Java API for ${template.simpleName.asString()}: ${e.message}",
          template,
        )
      }
    }
    return emptyList()
  }

  // ---------------------------------------------------------------------------------------------
  // Kotlin API
  // ---------------------------------------------------------------------------------------------

  private fun generateKotlin(template: KSClassDeclaration) {
    val templateFqn = template.qualifiedName?.asString() ?: error("Template has no qualified name")
    val targetPackage =
      stringArg(template, "targetPackage").ifEmpty {
        error("@ApiTemplate.targetPackage is required")
      }
    val targetName = generatedName(template)
    val aliases = allAliases(template)
    val functions = allFunctions(template)
    val markers = stringArgs(template, "annotations")
    val typeParams = selfTypeParameters(template, targetName)
    if (template.typeParameters.isNotEmpty() && typeParams.isEmpty()) {
      error(
        "Template type parameters are only supported as a single 'Self' parameter " +
          "(used to refer to the generated API)."
      )
    }
    val superType =
      templateFqn + if (typeParams.isEmpty()) "" else typeParams.values.joinToString(", ", "<", ">")

    val body = StringBuilder()
    aliases.forEach { alias ->
      body.appendLine("  typealias ${alias.name} = ${alias.kotlinType}")
    }
    aliases
      .filter { it.actionable }
      .forEach { alias ->
        body.appendLine("  typealias ${alias.name}Action<Res> = ${alias.name}.() -> Res")
      }
    body.appendLine()

    val seen = mutableSetOf<String>()
    functions
      .filter { it.hasAnnotation(Annotations.ActionEnd) || it.hasAnnotation(Annotations.ToAction) }
      .forEach { function ->
        if (function.typeParameters.isNotEmpty()) {
          logger.warn(
            "Action annotations on generic function ${function.simpleName.asString()} are not supported",
            function,
          )
          return@forEach
        }
        val defaults =
          when {
            function.hasAnnotation(Annotations.ToAction) ->
              annotationStringArgs(function, Annotations.ToAction, "defaults")
            function.hasAnnotation(Annotations.ActionEnd) ->
              annotationStringArgs(function, Annotations.ActionEnd, "defaults")
            else -> emptyList()
          }
        function.parameters.forEachIndexed { index, parameter ->
          if (parameter.hasDefault && defaults.getOrNull(index).isNullOrEmpty()) {
            logger.warn(
              "Default parameter '${parameter.name?.asString()}' on action function " +
                "${function.simpleName.asString()} has no annotation default at index $index. " +
                "Add it to @ActionEnd/@ToAction defaults.",
              function,
            )
          }
        }
        val name = function.simpleName.asString()
        val params = function.paramsKotlin(typeParams, defaults)
        val args = function.argNames()
        val receiverAlias = function.extensionReceiver?.resolve()?.actionableAlias(aliases)
        val returnAlias = function.returnType?.resolve()?.actionableAlias(aliases)

        // @ToAction: make a receiver action callable before its position is known.
        if (function.hasAnnotation(Annotations.ToAction) && receiverAlias != null) {
          val resultType = returnAlias?.name ?: renderKotlinType(function.returnType, typeParams)
          val key = "creator:$name(${function.parameters.joinToString { paramKey(it) }})"
          if (seen.add(key)) {
            body.appendLine("  fun $name($params): ${receiverAlias.name}Action<$resultType> =")
            body.appendLine("    { this.$name($args) }")
            body.appendLine()
          }
        }

        // @ActionEnd: run an action at the position produced by this function.
        if (
          function.hasAnnotation(Annotations.ActionEnd) &&
            receiverAlias == null &&
            returnAlias != null
        ) {
          val key =
            "apply:${returnAlias.name}:$name(${function.parameters.joinToString { paramKey(it) }})"
          if (seen.add(key)) {
            body.appendLine("  fun <Res> ${returnAlias.name}Action<Res>.$name($params) =")
            body.appendLine("    this@$targetName.$name($args).run(this)")
            body.appendLine()
          }
        }
      }

    val text = buildString {
      appendLine(
        "// Generated by PeroxiCore KSP from ${template.simpleName.asString()}. Do not edit."
      )
      appendLine("package $targetPackage")
      appendLine()
      markers.forEach { appendLine("@$it") }
      appendLine("interface $targetName : $superType {")
      if (aliases.isEmpty() && body.isBlank()) {
        appendLine()
      } else {
        append(body)
      }
      appendLine("}")
    }
    writeKotlin(targetPackage, targetName, text, hierarchyFiles(template))
    logger.info("Generated $targetPackage.$targetName from ${template.simpleName.asString()}")
  }

  // ---------------------------------------------------------------------------------------------
  // Java facade
  // ---------------------------------------------------------------------------------------------

  private fun generateJava(
    template: KSClassDeclaration,
    names: Map<String, String>,
  ) {
    val javaPackage = stringArg(template, "javaPackage")
    val javaName = stringArg(template, "javaName")
    if (javaPackage.isEmpty()) error("@ApiTemplate.javaPackage is required for generateJava")
    val owner = names[template.qualifiedName?.asString()] ?: error("Missing generated API name")
    val ownerType = ClassName.bestGuess(owner)
    val outerType = ClassName.get(javaPackage, javaName)
    val typeParams = selfTypeParameters(template, owner)
    val aliases = allAliases(template)
    val aliasByName = aliases.associateBy { it.name }
    val handles = aliases.filter { it.actionable && it.javaName.isNotEmpty() }
    if (handles.isEmpty()) {
      logger.warn("No @Actionable type with javaName for Java facade", template)
      return
    }
    fun handleType(alias: AliasInfo): ClassName = outerType.nestedClass(alias.javaName)

    val worldMethods = mutableListOf<MethodSpec>()
    val handleMethods = handles.associate { it.name to mutableListOf<MethodSpec>() }
    val seen = mutableSetOf<String>()

    allFunctions(template).forEach { function ->
      if (function.typeParameters.isNotEmpty()) return@forEach
      val name = function.simpleName.asString()
      val receiverAlias = function.extensionReceiver?.resolve()?.actionableAlias(aliases)
      val returnAlias = function.returnType?.resolve()?.actionableAlias(aliases)
      val receiverHandle =
        receiverAlias?.let { aliasByName[it.name] }?.takeIf { it.javaName.isNotEmpty() }
      val returnHandle =
        returnAlias?.let { aliasByName[it.name] }?.takeIf { it.javaName.isNotEmpty() }
      val key =
        "${receiverHandle?.javaName}:$name(${function.parameters.joinToString { paramKey(it) }})"
      if (!seen.add(key)) return@forEach

      val paramNames = function.parameters.map { it.name?.asString() ?: "arg" }
      val method =
        MethodSpec.methodBuilder(name).addModifiers(Modifier.PUBLIC).apply {
          function.parameters.forEach { parameter ->
            addParameter(
              javaTypeName(parameter.type.resolve(), typeParams),
              parameter.name?.asString() ?: "arg",
            )
          }
        }
      val call = callCode(name, paramNames, withReceiver = receiverHandle != null)

      if (receiverHandle != null) {
        when {
          returnHandle != null -> {
            method.returns(handleType(returnHandle))
            method.addStatement(
              "return new \$T(this.\$N, \$L)",
              handleType(returnHandle),
              "scope",
              call,
            )
          }

          function.returnsUnit() -> {
            method.returns(handleType(receiverHandle))
            method.addStatement("\$L", call)
            method.addStatement("return this")
          }

          else -> {
            method.returns(javaTypeName(function.returnType?.resolve(), typeParams))
            method.addStatement("return \$L", call)
          }
        }
        handleMethods.getValue(receiverHandle.name) += method.build()
      } else if (function.extensionReceiver == null) {
        when {
          returnHandle != null -> {
            method.returns(handleType(returnHandle))
            method.addStatement(
              "return new \$T(this.\$N, \$L)",
              handleType(returnHandle),
              "scope",
              call,
            )
          }

          function.returnsUnit() -> {
            method.returns(outerType)
            method.addStatement("\$L", call)
            method.addStatement("return this")
          }

          else -> {
            method.returns(javaTypeName(function.returnType?.resolve(), typeParams))
            method.addStatement("return \$L", call)
          }
        }
        worldMethods += method.build()
      }
    }

    val world =
      TypeSpec.classBuilder(javaName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addField(FieldSpec.builder(ownerType, "scope", Modifier.PRIVATE, Modifier.FINAL).build())
        .addMethod(
          MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ownerType, "scope")
            .addStatement("this.\$N = \$N", "scope", "scope")
            .build()
        )
        .addMethods(worldMethods)
        .addTypes(
          handles.map { alias ->
            TypeSpec.classBuilder(alias.javaName)
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
              .addField(
                FieldSpec.builder(ownerType, "scope", Modifier.PRIVATE, Modifier.FINAL).build()
              )
              .addField(
                FieldSpec.builder(
                    javaTypeName(alias.targetType, typeParams),
                    "receiver",
                    Modifier.PRIVATE,
                    Modifier.FINAL,
                  )
                  .build()
              )
              .addMethod(
                MethodSpec.constructorBuilder()
                  .addModifiers(Modifier.PUBLIC)
                  .addParameter(ownerType, "scope")
                  .addParameter(javaTypeName(alias.targetType, typeParams), "receiver")
                  .addStatement("this.\$N = \$N", "scope", "scope")
                  .addStatement("this.\$N = \$N", "receiver", "receiver")
                  .build()
              )
              .addMethods(handleMethods.getValue(alias.name))
              .build()
          }
        )
        .build()

    val javaFile =
      JavaFile.builder(javaPackage, world)
        .addFileComment("Generated by PeroxiCore KSP. Do not edit.")
        .skipJavaLangImports(true)
        .build()
    writeJava(javaPackage, javaName, javaFile, hierarchyFiles(template))
    logger.info("Generated Java facade $javaPackage.$javaName")
  }

  private fun callCode(
    name: String,
    params: List<String>,
    withReceiver: Boolean,
  ): CodeBlock {
    val builder = CodeBlock.builder()
    builder.add("this.\$N.\$N(", "scope", name)
    var first = true
    if (withReceiver) {
      builder.add("this.receiver")
      first = false
    }
    params.forEach { param ->
      if (!first) builder.add(", ")
      builder.add("\$N", param)
      first = false
    }
    builder.add(")")
    return builder.build()
  }

  // ---------------------------------------------------------------------------------------------
  // Symbols
  // ---------------------------------------------------------------------------------------------

  private fun generatedName(template: KSClassDeclaration): String =
    stringArg(template, "name").ifEmpty {
      template.simpleName.asString().removeSuffix("Template")
    }

  private fun generatedFqn(template: KSClassDeclaration): String {
    val pkg = stringArg(template, "targetPackage")
    if (pkg.isEmpty()) error("@ApiTemplate.targetPackage is required")
    return "$pkg.${generatedName(template)}"
  }

  /**
   * Templates may declare a single "Self" type parameter to refer to the generated API (for example
   * Promise<Self, ...>). The generated interface extends the template with itself.
   */
  private fun selfTypeParameters(
    template: KSClassDeclaration,
    targetName: String,
  ): Map<String, String> =
    template.typeParameters
      .filter { it.name.asString() == "Self" }
      .associate { it.name.asString() to targetName }

  private fun annotation(
    symbol: KSAnnotated,
    fqName: String,
  ): KSAnnotation? =
    symbol.annotations.firstOrNull {
      it.annotationType.resolve().declaration.qualifiedName?.asString() == fqName
    }

  private fun KSAnnotated.hasAnnotation(fqName: String) = annotation(this, fqName) != null

  private fun stringArg(
    symbol: KSAnnotated,
    name: String,
  ): String {
    val value =
      annotation(symbol, Annotations.ApiTemplate)
        ?.arguments
        ?.firstOrNull { it.name?.asString() == name }
        ?.value
    return value as? String ?: ""
  }

  private fun stringArgs(
    symbol: KSAnnotated,
    name: String,
  ): List<String> = annotationStringArgs(symbol, Annotations.ApiTemplate, name)

  private fun annotationStringArgs(
    symbol: KSAnnotated,
    annotationFqName: String,
    name: String,
  ): List<String> {
    val value =
      annotation(symbol, annotationFqName)
        ?.arguments
        ?.firstOrNull { it.name?.asString() == name }
        ?.value
    return when (value) {
      is List<*> -> value.mapNotNull { it as? String }
      is Array<*> -> value.mapNotNull { it as? String }
      is String -> listOf(value)
      else -> emptyList()
    }
  }

  private data class AliasInfo(
    val name: String,
    val fqn: String,
    val aliasFqn: String,
    val kotlinType: String,
    val targetType: KSType,
    val actionable: Boolean,
    val javaName: String,
  )

  private fun allAliases(template: KSClassDeclaration): List<AliasInfo> {
    val result = LinkedHashMap<String, AliasInfo>()
    visitHierarchy(template) { declaration ->
      declaration.declarations.filterIsInstance<KSTypeAlias>().forEach { alias ->
        val name = alias.simpleName.asString()
        if (result.containsKey(name)) return@forEach
        val resolved = alias.type.resolve()
        val javaName =
          annotation(alias, Annotations.Actionable)
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "javaName" }
            ?.value as? String ?: ""
        result[name] =
          AliasInfo(
            name = name,
            fqn = resolved.declaration.qualifiedName?.asString() ?: renderType(resolved),
            aliasFqn = alias.qualifiedName?.asString() ?: name,
            kotlinType = renderType(resolved),
            targetType = resolved,
            actionable = alias.hasAnnotation(Annotations.Actionable),
            javaName = javaName,
          )
      }
    }
    return result.values.toList()
  }

  private fun allFunctions(template: KSClassDeclaration): List<KSFunctionDeclaration> {
    val result = LinkedHashMap<String, KSFunctionDeclaration>()
    visitHierarchy(template) { declaration ->
      declaration.declarations.filterIsInstance<KSFunctionDeclaration>().forEach { function ->
        val name = function.simpleName.asString()
        if (name == "<init>" || name == "equals" || name == "hashCode" || name == "toString") {
          return@forEach
        }
        val key = functionKey(function)
        result.putIfAbsent(key, function)
      }
    }
    return result.values.toList()
  }

  private fun functionKey(function: KSFunctionDeclaration): String {
    val receiver = function.extensionReceiver?.resolve()?.let { typeKey(it) } ?: ""
    val params = function.parameters.joinToString(",") { paramKey(it) }
    return "${receiver}:${function.simpleName.asString()}:$params"
  }

  private fun paramKey(param: KSValueParameter): String {
    val type = param.type.resolve()
    return "${typeKey(type)}${if (param.hasDefault) "=default" else ""}"
  }

  private fun typeKey(type: KSType): String {
    val declaration = type.declaration
    val base = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    val args =
      if (type.arguments.isEmpty()) ""
      else
        type.arguments.joinToString(",", "<", ">") { arg ->
          arg.type?.resolve()?.let { typeKey(it) } ?: "*"
        }
    return base + args + if (type.isMarkedNullable) "?" else ""
  }

  private fun hierarchyFiles(template: KSClassDeclaration): List<KSFile> {
    val files = mutableListOf<KSFile>()
    visitHierarchy(template) { declaration ->
      declaration.containingFile?.let {
        if (it !in files) files += it
      }
    }
    return files
  }

  private fun visitHierarchy(
    root: KSClassDeclaration,
    action: (KSClassDeclaration) -> Unit,
  ) {
    val visited = mutableSetOf<String>()
    fun visit(declaration: KSClassDeclaration) {
      val fqn = declaration.qualifiedName?.asString() ?: return
      if (!visited.add(fqn)) return
      action(declaration)
      declaration.superTypes.forEach { ref ->
        val parent = runCatching { ref.resolve().declaration as? KSClassDeclaration }.getOrNull()
        if (parent != null) visit(parent)
      }
    }
    visit(root)
  }

  private fun KSType.actionableAlias(aliases: List<AliasInfo>): AliasInfo? {
    val declaration = declaration
    val simple = declaration.simpleName.asString()
    val qn = declaration.qualifiedName?.asString()
    return aliases.firstOrNull {
      it.actionable && (it.name == simple || it.fqn == qn || it.aliasFqn == qn)
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Type rendering
  // ---------------------------------------------------------------------------------------------

  private fun renderKotlinType(
    type: KSTypeReference?,
    typeParams: Map<String, String> = emptyMap(),
  ): String {
    if (type == null) return "Unit"
    val resolved = runCatching { type.resolve() }.getOrNull() ?: return "Any"
    return renderType(resolved).substituteTypeParams(typeParams).let {
      if (it == "kotlin.Unit") "Unit" else it
    }
  }

  private fun simpleKotlinName(qn: String): String =
    when (qn) {
      "kotlin.Int" -> "Int"
      "kotlin.Float" -> "Float"
      "kotlin.Double" -> "Double"
      "kotlin.Long" -> "Long"
      "kotlin.Short" -> "Short"
      "kotlin.Byte" -> "Byte"
      "kotlin.Char" -> "Char"
      "kotlin.Boolean" -> "Boolean"
      "kotlin.String" -> "String"
      "kotlin.Any" -> "Any"
      "kotlin.Unit" -> "Unit"
      "kotlin.Pair" -> "Pair"
      "kotlin.Triple" -> "Triple"
      "kotlin.collections.List",
      "kotlin.collections.MutableList" -> "List"
      "kotlin.collections.Set",
      "kotlin.collections.MutableSet" -> "Set"
      "kotlin.collections.Map",
      "kotlin.collections.MutableMap" -> "Map"
      "kotlin.collections.Collection",
      "kotlin.collections.MutableCollection" -> "Collection"
      "kotlin.collections.Iterable",
      "kotlin.collections.MutableIterable" -> "Iterable"
      "kotlin.ranges.IntRange" -> "IntRange"
      "kotlin.ranges.LongRange" -> "LongRange"
      else -> if (qn.startsWith("kotlin.Function")) qn.substringAfterLast('.') else qn
    }

  private fun String.substituteTypeParams(typeParams: Map<String, String>): String {
    var result = this
    typeParams.forEach { (name, replacement) ->
      result = result.replace(Regex("\\b${Regex.escape(name)}\\b"), replacement)
    }
    return result
  }

  private fun renderType(type: KSType): String {
    val declaration = type.declaration
    val base =
      when (declaration) {
        is KSTypeParameter -> declaration.name.asString()
        is KSTypeAlias -> declaration.simpleName.asString()
        else ->
          declaration.qualifiedName?.asString()?.let(::simpleKotlinName)
            ?: declaration.simpleName.asString()
      }
    val args =
      if (type.arguments.isEmpty()) ""
      else
        type.arguments.joinToString(", ", "<", ">") { arg ->
          arg.type?.resolve()?.let { renderType(it) } ?: "*"
        }
    return base + args + if (type.isMarkedNullable) "?" else ""
  }

  private fun javaTypeName(
    type: KSType?,
    typeParams: Map<String, String> = emptyMap(),
    boxed: Boolean = false,
  ): TypeName {
    if (type == null) return TypeName.VOID
    val declaration = type.declaration
    if (declaration is KSTypeAlias) {
      return javaTypeName(declaration.type.resolve(), typeParams, boxed)
    }
    if (declaration is KSTypeParameter) {
      val mapped = typeParams[declaration.name.asString()]
      return if (mapped != null) ClassName.bestGuess(mapped) else ClassName.OBJECT
    }
    val qn = declaration.qualifiedName?.asString()
    val primitive =
      when (qn) {
        "kotlin.Int" -> TypeName.INT
        "kotlin.Float" -> TypeName.FLOAT
        "kotlin.Double" -> TypeName.DOUBLE
        "kotlin.Long" -> TypeName.LONG
        "kotlin.Short" -> TypeName.SHORT
        "kotlin.Byte" -> TypeName.BYTE
        "kotlin.Char" -> TypeName.CHAR
        "kotlin.Boolean" -> TypeName.BOOLEAN
        else -> null
      }
    if (primitive != null) {
      return if (boxed || type.isMarkedNullable) primitive.box() else primitive
    }
    if (qn == "kotlin.Unit") {
      return if (boxed) ClassName.get("kotlin", "Unit") else TypeName.VOID
    }
    val args =
      type.arguments.mapNotNull { argument ->
        argument.type?.resolve()?.let { javaTypeName(it, typeParams, boxed = true) }
      }
    val raw: TypeName =
      when (qn) {
        "kotlin.String" -> ClassName.get("java.lang", "String")
        "kotlin.Any" -> ClassName.OBJECT
        "kotlin.collections.List",
        "kotlin.collections.MutableList" -> ClassName.get("java.util", "List")
        "kotlin.collections.Set",
        "kotlin.collections.MutableSet" -> ClassName.get("java.util", "Set")
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap" -> ClassName.get("java.util", "Map")
        "kotlin.collections.Collection",
        "kotlin.collections.MutableCollection" -> ClassName.get("java.util", "Collection")
        "kotlin.collections.Iterable",
        "kotlin.collections.MutableIterable" -> ClassName.get("java.lang", "Iterable")
        else -> {
          if (qn != null && qn.startsWith("kotlin.Function")) {
            ClassName.get("kotlin.jvm.functions", qn.substringAfterLast('.'))
          } else {
            ClassName.bestGuess(qn ?: declaration.simpleName.asString())
          }
        }
      }
    return if (args.isEmpty()) raw
    else ParameterizedTypeName.get(raw as ClassName, *args.toTypedArray())
  }

  private fun KSFunctionDeclaration.paramsKotlin(
    typeParams: Map<String, String>,
    defaults: List<String> = emptyList(),
  ): String =
    parameters
      .mapIndexed { index, parameter ->
        val name = parameter.name?.asString() ?: "arg"
        val default = defaults.getOrNull(index)?.takeIf { it.isNotEmpty() }
        buildString {
          append(name)
          append(": ")
          append(renderKotlinType(parameter.type, typeParams))
          if (default != null) {
            append(" = ")
            append(default)
          }
        }
      }
      .joinToString(", ")

  private fun KSFunctionDeclaration.argNames(): String =
    parameters.joinToString(", ") { it.name?.asString() ?: "arg" }

  private fun KSFunctionDeclaration.returnsUnit(): Boolean =
    returnType?.resolve()?.declaration?.qualifiedName?.asString() == "kotlin.Unit"

  // ---------------------------------------------------------------------------------------------
  // Output
  // ---------------------------------------------------------------------------------------------

  private fun writeKotlin(
    packageName: String,
    fileName: String,
    text: String,
    files: List<KSFile>,
  ) {
    val dependencies =
      if (files.isEmpty()) Dependencies(false) else Dependencies(false, *files.toTypedArray())
    codeGenerator.createNewFile(dependencies, packageName, fileName).bufferedWriter().use {
      it.write(text)
    }
  }

  private fun writeJava(
    packageName: String,
    fileName: String,
    javaFile: JavaFile,
    files: List<KSFile>,
  ) {
    val dependencies =
      if (files.isEmpty()) Dependencies(false) else Dependencies(false, *files.toTypedArray())
    codeGenerator.createNewFile(dependencies, packageName, fileName, "java").bufferedWriter().use {
      javaFile.writeTo(it)
    }
  }

  @AutoService(SymbolProcessorProvider::class)
  class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) =
      TemplateApiProcessor(environment.codeGenerator, environment.logger)
  }
}

private object Annotations {
  const val ApiTemplate = "peroxicore.annotations.ApiTemplate"
  const val Actionable = "peroxicore.annotations.Actionable"
  const val ActionEnd = "peroxicore.annotations.ActionEnd"
  const val ToAction = "peroxicore.annotations.ToAction"
}
