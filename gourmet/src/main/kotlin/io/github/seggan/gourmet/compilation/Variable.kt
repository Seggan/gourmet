package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.typing.Type

data class Variable(val name: String, val type: Type, val mapped: List<String>) {
    val size by mapped::size
}