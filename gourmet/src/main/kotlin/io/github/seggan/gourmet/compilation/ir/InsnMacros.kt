package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.util.randomString

class InsnMacros private constructor() {

    private val insns = mutableListOf<Insn>()

    operator fun Insn.unaryPlus() {
        insns.add(this)
    }

    operator fun List<Insn>.unaryPlus() {
        insns.addAll(this)
    }

    operator fun String.invoke(vararg args: Argument) {
        insns.add(Insn(this, *args))
    }

    operator fun String.invoke(vararg args: Argument, block: InsnMacros.() -> Unit) {
        val insnMacros = InsnMacros()
        block(insnMacros)
        insns.add(Insn(this, *args, Argument.Block(insnMacros.insns)))
    }

    fun withStack(stack: String, block: InsnMacros.() -> Unit) {
        val insnMacros = InsnMacros()
        block(insnMacros)
        insns.addAll(insnMacros.insns.map { it.copy(stack = stack) })
    }

    companion object {

        fun dup() = insns {
            val temp = randomVar()
            "def"(temp)
            "pop"(temp)
            "push"(temp)
            "push"(temp)
            "del"(temp)
        }

        fun neg(arg: Argument) = insns {
            "push"(Argument.Number(0))
            "sub"(arg)
        }

        fun bool(arg: Argument, not: Boolean = false) = insns {
            "push"(Argument.Number(if (not) 1 else 0))
            "if"(arg) {
                "pop"(Argument.Variable.NULL)
                "push"(Argument.Number(if (not) 0 else 1))
            }
        }

        fun sign() = insns {
            val tempSign = randomVar()
            "def"(tempSign)
            "pop"(tempSign)
            "if"(tempSign) {
                val randomStack = randomStack()
                "def"(Argument.Stack(randomStack))
                withStack(randomStack) {
                    +neg(Argument.Number(1))
                    "push"(Argument.Number(1))
                    "rot"(tempSign)
                    "pop"(Argument.Variable.NULL)
                    "pop"(tempSign)
                }
                "undef"(Argument.Stack(randomStack))
            }
            "push"(tempSign)
            "del"(tempSign)
        }

        fun eq(arg: Argument, not: Boolean = false) = insns {
            "sub"(arg)
            +useStack { bool(it, not = !not) }
        }

        fun lt(arg: Argument) = insns {
            "sub"(arg)
            +sign()
            +neg(Argument.Number(1))
            +useStack(::eq)
        }

        fun ltOrEq(arg: Argument) = insns {
            "sub"(arg)
            +sign()
            +eq(Argument.Number(1), not = true)
        }

        fun gt(arg: Argument) = insns {
            "sub"(arg)
            +sign()
            +eq(Argument.Number(1))
        }

        fun gtOrEq(arg: Argument) = insns {
            "sub"(arg)
            +sign()
            +neg(Argument.Number(1))
            +useStack { eq(it, not = true) }
        }

        fun useStack(fn: (Argument) -> List<Insn>) = insns {
            val temp = randomVar()
            "def"(temp)
            "pop"(temp)
            +fn(temp)
            "del"(temp)
        }

        private fun insns(block: InsnMacros.() -> Unit): List<Insn> {
            val insnMacros = InsnMacros()
            block(insnMacros)
            return insnMacros.insns
        }
    }
}

private fun randomVar() = Argument.Variable("TEMP" + randomString())
private fun randomStack() = "TEMP" + randomString()