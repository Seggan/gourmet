package io.github.seggan.gourmet

import io.github.seggan.gourmet.antlr.GourmetLexer
import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.IrCompiler
import io.github.seggan.gourmet.compilation.IrGenerator
import io.github.seggan.gourmet.compilation.ir.toGraph
import io.github.seggan.gourmet.compilation.optimization.BlockOptimizer
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.parsing.GourmetVisitor
import io.github.seggan.gourmet.typing.TypeChecker
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.InputStream
import kotlin.io.path.*

fun main(args: Array<String>) {
    val std = getAst(
        "std.gourmet",
        GourmetVisitor::class.java.getResourceAsStream("/std.gourmet")!!,
    )
    val file = Path(args[0])
    val main = std + getAst(file.name, file.inputStream())
    val checked = TypeChecker.check(main)
    val (ir, strings) = IrGenerator.generate(checked)
    val optimized = ir.map { it.copy(body = BlockOptimizer.optimize(it.body)) }
    val dot = optimized.joinToString("\n") { it.toGraph() }
    Path("graph.dot").writeText("digraph G { $dot }")
    Runtime.getRuntime().exec("dot -Tpng graph.dot -o graph.png")
    val asm = IrCompiler.compile(optimized, strings)
    Path("${file.nameWithoutExtension}.recipe").writeText(asm)
}

private fun getAst(fileName: String, file: InputStream): AstNode.File<Location> {
    Location.currentFile = fileName
    val parser = GourmetParser(CommonTokenStream(GourmetLexer(CharStreams.fromStream(file))))
    return GourmetVisitor.visitFile(parser.file())
}