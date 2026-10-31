package peroxicore.ciallo

import org.antlr.v4.kotlinruntime.*
import peroxicore.parsers.*
import java.io.*

internal object CialloErrorListener : BaseErrorListener() {
  override fun syntaxError(
    recognizer: Recognizer<*, *>,
    offendingSymbol: Any?,
    line: Int,
    charPositionInLine: Int,
    msg: String,
    e: RecognitionException?,
  ) {
    throw CialloSyntaxException(msg, line, charPositionInLine, e)
  }
}

/**
 * Convenience entry point around the generated parser and the interpreter.
 *
 * Globals belong to [CialloEnvironment], so engines created with the same
 * environment share mounted variables; create a new environment for an isolated
 * global scope and sandbox.
 */
class CialloEngine(
  val environment: CialloEnvironment = CialloEnvironment(),
) {
  val globalScope: CialloScope get() = environment.globalScope

  fun execute(source: String, sourceName: String = "<ciallo>"): CialloValue {
    val lexer = CialloLexer(CharStreams.fromString(source, sourceName))
    lexer.removeErrorListeners()
    lexer.addErrorListener(CialloErrorListener)

    val parser = CialloParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    parser.addErrorListener(CialloErrorListener)

    val program = parser.program()
    return CialloInterpreter(environment, globalScope).execute(program)
  }

  fun executeFile(file: File): CialloValue = execute(file.readText(), file.name)
}
