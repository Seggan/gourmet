package io.github.seggan.gourmet

import io.github.seggan.gourmet.antlr.GourmetLexer
import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.BlockOptimizer
import io.github.seggan.gourmet.compilation.IrCompiler
import io.github.seggan.gourmet.compilation.IrGenerator
import io.github.seggan.gourmet.compilation.ir.CompiledFunction
import io.github.seggan.gourmet.compilation.ir.toGraph
import io.github.seggan.gourmet.parsing.GourmetVisitor
import io.github.seggan.gourmet.typing.TypeChecker
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.InputStream
import kotlin.io.path.*

fun main(args: Array<String>) {
    val std = getIr(
        "std.gourmet",
        GourmetVisitor::class.java.getResourceAsStream("/std.gourmet")!!,
        removeUnused = false
    )
    val file = Path(args[0])
    val main = getIr(file.name, file.inputStream(), std)
    val dot = main.joinToString("\n") { it.toGraph() }
    Path("graph.dot").writeText("digraph G { $dot }")
    Runtime.getRuntime().exec("dot -Tpng graph.dot -o graph.png")
    val asm = IrCompiler.compile(main)
    Path("${file.nameWithoutExtension}.recipe").writeText(asm)
}

private fun getIr(
    fileName: String,
    file: InputStream,
    external: List<CompiledFunction> = emptyList(),
    removeUnused: Boolean = true
): List<CompiledFunction> {
    val stream = CommonTokenStream(GourmetLexer(CharStreams.fromStream(file)))
    val parsed = GourmetParser(stream).file()
    Location.currentFile = fileName
    val ast = GourmetVisitor.visitFile(parsed)
    val typedAst = TypeChecker.check(ast, external.map { it.signature })
    val compiled = IrGenerator.generate(typedAst, external, removeUnused)
    return compiled.map { it.copy(body = BlockOptimizer.optimize(it.body)) }
}