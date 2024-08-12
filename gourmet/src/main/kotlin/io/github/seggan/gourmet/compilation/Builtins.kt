package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.typing.Type

object Builtins {

    val builtins: List<Builtin> = listOf(
        object : Builtin {
            override val name = "println"
            override val signature = Type.Function(
                emptyList(),
                listOf(Type.Primitive.NUMBER),
                Type.Unit
            )

            override fun Compiler.compile(args: List<AstNode.Expression<Type>>) {
                compileExpression(args.single())
                code("print", "flush")
            }
        },
        object : Builtin {
            override val name = "println"
            override val signature = Type.Function(
                emptyList(),
                listOf(Type.Primitive.CHAR),
                Type.Unit
            )

            override fun Compiler.compile(args: List<AstNode.Expression<Type>>) {
                compileExpression(args.single())
                code("print", "flushStr")
            }
        }
    )
}

interface Builtin {
    val name: String
    val signature: Type.Function

    fun Compiler.compile(args: List<AstNode.Expression<Type>>)
}