package io.github.seggan.gourmet.compilation.ir

import java.math.BigDecimal

sealed interface Argument {

    fun toIr(): String

    data class Number(val value: BigDecimal) : Argument {
        constructor(value: Int) : this(BigDecimal.valueOf(value.toLong()))
        constructor(value: Double) : this(BigDecimal.valueOf(value))
        override fun toIr(): String = value.toString()
    }

    data class Variable(val name: String) : Argument {
        override fun toIr(): String = "$$name"

        companion object {
            val NULL = Variable("null")
        }
    }

    data class Stack(val name: String) : Argument {
        override fun toIr(): String = "@$name"
    }

    data class Block(val insns: List<Insn>) : Argument {
        constructor(vararg insns: Insn) : this(insns.toList())
        override fun toIr(): String = insns.joinToString(
            separator = "\n",
            prefix = "{\n",
            postfix = "\n}"
        ) { it.toIr() }
    }

    data object UseStack : Argument {
        override fun toIr(): String = "{ nop; }"
    }
}

fun List<Insn>.block() = Argument.Block(this)
