package io.github.seggan.recipe

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.github.seggan.recipe.compilation.Compiler
import io.github.seggan.recipe.parsing.Parser
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val file = Path(args[0])
    val text = file.readText() + "\n"
    val std = ::main.javaClass.getResource("/std.recipe")!!.readText()
    val parsed = Parser.parseToEnd(std + "\n" + text)
    val compiled = Compiler(file.nameWithoutExtension, parsed).compile()
    val out = Path("${file.nameWithoutExtension}.chef")
    out.writeText(compiled.toCode())
}