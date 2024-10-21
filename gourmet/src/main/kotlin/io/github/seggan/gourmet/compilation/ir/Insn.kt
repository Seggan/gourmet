package io.github.seggan.gourmet.compilation.ir

import java.math.BigDecimal

data class Insn(
    val stack: String?,
    val insn: String,
    val args: List<Argument>
) {

    constructor(insn: String, vararg args: Argument, stack: String? = null) :
            this(stack, insn, args.toList())

    fun toIr(): String = buildString {
        if (stack != null) {
            append('@')
            append(stack)
            append('.')
        }
        append(insn)
        for (arg in args) {
            append(' ')
            append(arg.toIr())
        }
        appendLine(';')
    }

    @Suppress("FunctionName")
    companion object {
        fun Push(value: Int, stack: String? = null) = Insn("push", Argument.Number(value), stack = stack)
        fun Push(value: BigDecimal, stack: String? = null) = Insn("push", Argument.Number(value), stack = stack)
        fun Pop(stack: String? = null) = Insn("pop", stack = stack)
    }
}