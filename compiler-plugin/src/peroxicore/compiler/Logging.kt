package peroxicore.compiler

import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.resolve.source.*

fun MessageCollector.log(message: String) {
  report(CompilerMessageSeverity.LOGGING, "PREOXIDE COMPILER PLUGIN (IR): $message")
}

fun MessageCollector.reportErrorOnClass(
  irClass: IrClass,
  message: String,
) {
  val psi = irClass.source.getPsi()
  val location = MessageUtil.psiElementToMessageLocation(psi)
  report(CompilerMessageSeverity.ERROR, message, location)
}
