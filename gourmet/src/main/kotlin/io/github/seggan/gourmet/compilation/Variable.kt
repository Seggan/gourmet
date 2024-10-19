package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.util.randomString

data class Variable(val name: String, val type: Type, val mapped: List<String>) {
    val size by mapped::size

    companion object {
        fun generate(name: String, type: Type): Variable {
            return Variable(name, type, (0 until type.size).map { "${name}p$it${randomString()}" })
        }
    }
}

class Scope(private val variables: MutableList<Variable> = mutableListOf()) : MutableList<Variable> by variables