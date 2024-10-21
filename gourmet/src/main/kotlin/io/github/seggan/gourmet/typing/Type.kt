package io.github.seggan.gourmet.typing

import io.github.seggan.gourmet.parsing.AstNode

sealed interface Type {

    val tname: String
    val size: Int

    enum class Primitive(override val tname: String) : Type {
        NUMBER("Number"),
        BOOLEAN("Boolean"),
        CHAR("Char");

        override val size = 1

        override fun fillGeneric(generic: Type, type: Type): Type {
            return this
        }
    }

    data object Never : Type {
        override val tname = "Never"
        override val size = 0

        override fun isAssignableTo(other: Type): Boolean {
            return true
        }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return this
        }
    }

    data object Unit : Type {
        override val tname = "Unit"
        override val size = 0

        override fun fillGeneric(generic: Type, type: Type): Type {
            return this
        }
    }

    data class Pointer(val target: Type) : Type {
        override val tname = "${target.tname}*"
        override val size = 1

        override fun isAssignableTo(other: Type): Boolean {
            return when (other) {
                is Pointer -> target.isAssignableTo(other.target)
                else -> false
            }
        }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return Pointer(target.fillGeneric(generic, type))
        }
    }

    data class Structure(override val tname: String, val fields: List<Pair<String, Type>>) : Type {
        override val size = fields.sumOf { it.second.size }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return Structure(tname, fields.map { (name, type) -> name to type.fillGeneric(generic, type) })
        }
    }

    data class Generic(override val tname: String) : Type {
        override val size: Int
            get() = throw UnsupportedOperationException("Generic type has no size")

        override fun fillGeneric(generic: Type, type: Type): Type {
            return if (this == generic) type else this
        }
    }

    data class Function(val genericArgs: List<Type>, val args: List<Type>, val returnType: Type) : Type {
        override val tname = "[${genericArgs.joinToString(", ")}](${args.joinToString(", ")}) -> $returnType"
        override val size = 1

        override fun fillGeneric(generic: Type, type: Type): Type {
            return Function(
                genericArgs.filterNot { it == generic }.map { it.fillGeneric(generic, type) },
                args.map { it.fillGeneric(generic, type) },
                returnType.fillGeneric(generic, type)
            )
        }
    }

    fun isAssignableTo(other: Type): Boolean {
        return this == other
    }

    fun fillGeneric(generic: Type, type: Type): Type

    companion object {
        val STRING = Structure("String", listOf("length" to Primitive.NUMBER, "data" to Pointer(Primitive.CHAR)))
    }
}

val AstNode<TypeData>.realType: Type
    get() = extra.type
