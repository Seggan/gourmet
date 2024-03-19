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
    val parsed = Parser.parseToEnd(file.readText() + "\n")
    println(parsed)
    val compiled = Compiler(file.nameWithoutExtension, parsed).compile()
    println(compiled)
    val out = Path("${file.nameWithoutExtension}.chef")
    out.writeText(compiled.toCode())
}