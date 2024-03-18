package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.chef.ChefStatement
import io.github.seggan.gourmet.parsing.AstNode

class Compiler {

    fun compile(code: List<AstNode>): List<ChefStatement> {
        val macros = code.filterIsInstance<AstNode.Macro>()
        var expanded = code.filterIsInstance<AstNode.Invocation>()
        var lastExpanded: List<AstNode.Invocation>
        do {
            lastExpanded = expanded
            expanded = expandMacros(expanded, macros)
        } while (expanded != lastExpanded)
        println(expanded)
        TODO()
    }

    private fun expandMacros(code: List<AstNode.Invocation>, macros: List<AstNode.Macro>): List<AstNode.Invocation> {
        val macroNames = macros.associateBy { it.name }
        return code.flatMap { invoc ->
            if (invoc.name in macroNames) {
                val macro = macroNames[invoc.name]!!
                val args = macro.args.zip(invoc.args).toMap()
                macro.body.map { invocation ->
                    invocation.copy(
                        args = invocation.args.map { arg ->
                            when (arg) {
                                is AstNode.Variable -> args[arg.name] ?: arg
                                is AstNode.Block -> arg.copy(body = expandMacros(arg.body, macros))
                                else -> arg
                            }
                        }
                    )
                }
            } else {
                listOf(invoc.copy(
                    args = invoc.args.map { arg ->
                        if (arg is AstNode.Block) {
                            arg.copy(body = expandMacros(arg.body, macros))
                        } else {
                            arg
                        }
                    }
                ))
            }
        }
    }
}