package io.github.seggan.gourmet

import io.github.seggan.gourmet.antlr.GourmetLexer
import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.Compiler
import io.github.seggan.gourmet.parsing.GourmetVisitor
import io.github.seggan.gourmet.parsing.stringify
import io.github.seggan.gourmet.typing.TypeChecker
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val file = Path(args[0])
    val stream = CommonTokenStream(GourmetLexer(CharStreams.fromPath(file)))
    val parsed = GourmetParser(stream).file()
    Location.currentFile = file.fileName.toString()
    val ast = GourmetVisitor.visitFile(parsed)
    val typedAst = TypeChecker.check(ast)
    println(typedAst.stringify())
    val compiled = Compiler(typedAst).compile()
    Path("${file.nameWithoutExtension}.recipe").writeText(compiled)
}