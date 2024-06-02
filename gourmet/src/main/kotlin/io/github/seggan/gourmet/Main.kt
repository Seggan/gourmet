package io.github.seggan.gourmet

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.github.seggan.gourmet.compilation.Compiler
import io.github.seggan.gourmet.parsing.Parser
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val file = Path(args[0])
    val text = file.readText()
    val parsed = Parser.parseToEnd(text)
    val functions = mutableSetOf<String>()
    val compiled = StringBuilder()
    for (expr in parsed) {
        val compiler = Compiler(expr, functions)
        compiled.appendLine(compiler.compile())
        functions.addAll(compiler.functions)
    }
    compiled.appendLine("call \$main;")
    val out = Path("${file.nameWithoutExtension}.recipe")
    out.writeText(text = compiled)
}