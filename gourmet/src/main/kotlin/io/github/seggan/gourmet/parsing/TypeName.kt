package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser

sealed interface TypeName {

    val name: String

    data class Simple(override val name: String) : TypeName

    data class Generic(override val name: String, val generics: List<TypeName>) : TypeName

    data class Pointer(val type: TypeName) : TypeName {
        override val name = "${type.name}*"
    }

    companion object {
        fun parse(ctx: GourmetParser.TypeContext): TypeName {
            return if (ctx.STAR() != null) {
                Pointer(parse(ctx.type()))
            } else if (ctx.generic() != null) {
                Generic(ctx.Identifier().text, ctx.generic().type().map(::parse))
            } else {
                Simple(ctx.Identifier().text)
            }
        }
    }
}