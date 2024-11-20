package io.github.seggan.gourmet.typing

import io.github.seggan.gourmet.util.Location

sealed interface TypeData {

    val type: Type
    val location: Location

    data class Empty(override val location: Location) : TypeData {
        override val type = Type.Unit
    }

    data class Basic(override val type: Type, override val location: Location) : TypeData

    data class FunctionCall(
        override val type: Type,
        val call: Type.Function,
        val overload: Signature,
        override val location: Location
    ) : TypeData
}