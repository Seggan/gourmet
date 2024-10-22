package io.github.seggan.gourmet.typing

sealed interface TypeData {

    val type: Type

    data object Empty : TypeData {
        override val type = Type.Unit
    }

    data class Basic(override val type: Type) : TypeData

    data class FunctionCall(override val type: Type, val overload: Signature) : TypeData
}