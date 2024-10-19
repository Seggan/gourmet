package io.github.seggan.gourmet.compilation.ir

data class Instruction(
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
}