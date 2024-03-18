package io.github.seggan.gourmet

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.github.seggan.gourmet.parsing.Parser
import kotlin.io.path.Path
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val parsed = Parser.parseToEnd(Path(args[0]).readText() + "\n")
    println(parsed)
}