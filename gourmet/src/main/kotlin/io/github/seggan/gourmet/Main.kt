package io.github.seggan.gourmet

import io.github.seggan.gourmet.antlr.GourmetLexer
import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.BlockOptimizer
import io.github.seggan.gourmet.compilation.IrCompiler
import io.github.seggan.gourmet.compilation.ir.toGraph
import io.github.seggan.gourmet.parsing.GourmetVisitor
import io.github.seggan.gourmet.parsing.stringify
import io.github.seggan.gourmet.typing.TypeChecker
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.Path
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val file = Path(args[0])
    val stream = CommonTokenStream(GourmetLexer(CharStreams.fromPath(file)))
    val parsed = GourmetParser(stream).file()
    Location.currentFile = file.fileName.toString()
    val ast = GourmetVisitor.visitFile(parsed)
    val typedAst = TypeChecker.check(ast)
    println(typedAst.stringify())
    val compiled = IrCompiler.compile(typedAst)
    val optimized = compiled.map { it.copy(body = BlockOptimizer.optimize(it.body)) }
    val dot = optimized.joinToString("\n") { it.toGraph() }
    Path("graph.dot").writeText(dot)
    Runtime.getRuntime().exec("dot -Tpng graph.dot -o graph.png")
}