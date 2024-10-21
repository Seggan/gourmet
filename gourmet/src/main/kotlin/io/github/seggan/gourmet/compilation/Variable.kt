package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.Argument
import io.github.seggan.gourmet.compilation.ir.Insn
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.util.randomString

data class Variable(val name: String, val type: Type, val mapped: List<String>) {
    val size by mapped::size

    fun push(stack: String? = null): List<Insn> {
        return mapped.map { Insn("push", Argument.Variable(it), stack = stack) }
    }

    fun pop(stack: String? = null): List<Insn> {
        return mapped.reversed().map { Insn("pop", Argument.Variable(it), stack = stack) }
    }

    companion object {
        fun generate(name: String, type: Type): Variable {
            return Variable(name, type, (0 until type.size).map { "${name}p$it${randomString()}" })
        }
    }
}

class Scope(private val variables: MutableList<Variable> = mutableListOf()) : MutableList<Variable> by variables