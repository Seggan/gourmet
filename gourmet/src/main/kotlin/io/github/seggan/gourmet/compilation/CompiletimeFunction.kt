package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.Insn
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.typing.Signature
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeData

enum class CompiletimeFunction(val signature: Signature) {
    SIZEOF(
        Signature(
            "sizeof",
            Type.Function(listOf(Type.Generic("T")), emptyList(), Type.Primitive.NUMBER)
        )
    ) {
        override fun IrGenerator.compile(function: AstNode.FunctionCall<TypeData>) = buildBlock {
            val call = (function.extra as TypeData.FunctionCall).call
            +Insn.Push(call.genericArgs.single().size)
        }
    },
    ASM(
        Signature(
            "asm",
            Type.Function(
                listOf(Type.Generic("R")),
                listOf(Type.STRING),
                Type.Generic("R")
            )
        )
    ) {
        override fun IrGenerator.compile(function: AstNode.FunctionCall<TypeData>) = buildBlock {
            val literal = function.args.single()
            if (literal !is AstNode.StringLiteral) {
                throw CompilationException("asm requires a string literal", literal.extra.location)
            }
            val value = literal.value
            val mangled = variableReplacementRegex.replace(value) {
                val name = it.groupValues[1]
                val part = it.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val variable = getVariable(name)
                    ?: throw CompilationException("Variable not found: $name", literal.extra.location)
                '$' + variable.mapped[part]
            }
            +Insn.raw(mangled)
        }
    },
    TRANSMUTE(
        Signature(
            "transmute",
            Type.Function(
                listOf(Type.Generic("T"), Type.Generic("R")),
                listOf(Type.Generic("T")),
                Type.Generic("R")
            )
        )
    ) {
        override fun IrGenerator.compile(function: AstNode.FunctionCall<TypeData>) =
            compileExpression(function.args.single()) // nothing changes, it just a type checker thing
    },
    ;

    abstract fun IrGenerator.compile(function: AstNode.FunctionCall<TypeData>): Blocks

    companion object {
        val signatures = entries.associateBy { it.signature }
    }
}

private val variableReplacementRegex = Regex("""\[([a-zA-Z_][a-zA-Z0-9_]*)(?::(\d+))?]""")