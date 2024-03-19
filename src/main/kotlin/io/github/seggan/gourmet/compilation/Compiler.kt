package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.Pool
import io.github.seggan.gourmet.chef.ChefProgram
import io.github.seggan.gourmet.chef.ChefStack
import io.github.seggan.gourmet.chef.ChefStatement
import io.github.seggan.gourmet.parsing.AstNode
import java.lang.reflect.InvocationTargetException

/*
Stack 1: Return stack
Stack 2: Argument stack
Stack 3: Main stack
 */
@Suppress("unused")
class Compiler(private val sourceName: String, private val code: List<AstNode>) {

    private val constants = mutableMapOf<Int, String>()
    private val allRegisters = mutableListOf<Pair<Int, String>>()
    private val tempRegisters = Pool(::getNewRegister)
    private val variables = mutableMapOf<String, String>()

    private var registerIndex = 0

    private val instructions = mutableListOf<ChefStatement>()

    private val mainStack = ChefStack(3)
    private var currentStack = mainStack

    fun compile(): ChefProgram {
        val macros = code.filterIsInstance<AstNode.Macro>()
        var expanded = code.filterIsInstance<AstNode.Invocation>()
        var lastExpanded: List<AstNode.Invocation>
        do {
            lastExpanded = expanded
            expanded = expandMacros(expanded, macros)
        } while (expanded != lastExpanded)
        compileBlock(expanded)
        return ChefProgram(sourceName, allRegisters, instructions, listOf())
    }

    private fun getNewRegister(initial: Int = 0): String {
        val reg = buildString {
            var i = registerIndex++
            do {
                append(REGISTER_VALID_CHARS[i % REGISTER_VALID_CHARS.length])
                i /= REGISTER_VALID_CHARS.length
            } while (i > 0)
        }
        allRegisters += initial to reg
        return reg
    }

    private fun getConstant(constant: Int): String {
        return constants.computeIfAbsent(constant, ::getNewRegister)
    }

    private fun AstNode.Expression.getRegister(): String {
        return when (this) {
            is AstNode.Number -> getConstant(value)
            is AstNode.Register -> variables.getOrPut(name) { throw IllegalArgumentException("Invalid register: $name") }
            is AstNode.Block -> {
                compileBlock(body)
                val reg = getNewRegister()
                instructions += ChefStatement.Pop(reg, currentStack)
                reg
            }

            else -> throw IllegalArgumentException("Invalid expression type: $this")
        }
    }

    private fun AstNode.Expression.varToRegister(): String {
        return if (this is AstNode.Register) {
            variables.getOrPut(name) { throw IllegalArgumentException("Invalid register: $name") }
        } else {
            throw IllegalArgumentException("Invalid expression type: $this")
        }
    }

    private fun compileBlock(block: List<AstNode.Invocation>) {
        for (insn in block) {
            try {
                Compiler::class.java.getMethod(
                    "i" + insn.name,
                    List::class.java
                ).invoke(this@Compiler, insn.args)

            } catch (e: NoSuchMethodException) {
                throw IllegalArgumentException("Invalid instruction: ${insn.name}")
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    //<editor-fold desc="Builtins">
    fun ipush(args: List<AstNode.Expression>) {
        instructions += ChefStatement.Push(args[0].getRegister(), currentStack)
    }

    fun ipop(args: List<AstNode.Expression>) {
        instructions += ChefStatement.Pop(args[0].varToRegister(), currentStack)
    }

    fun idef(args: List<AstNode.Expression>) {
        val arg = args[0]
        if (arg is AstNode.Register) {
            variables[arg.name] = getNewRegister()
        } else {
            throw IllegalArgumentException("Invalid register: $arg")
        }
    }
    //</editor-fold>
}

private fun expandMacros(code: List<AstNode.Invocation>, macros: List<AstNode.Macro>): List<AstNode.Invocation> {
    val macroNames = macros.associateBy { it.name }
    return code.flatMap { invoc ->
        if (invoc.name in macroNames) {
            val macro = macroNames[invoc.name]!!
            val args = macro.args.zip(invoc.args).toMap()
            macro.body.map { invocation ->
                invocation.copy(
                    args = invocation.args.map { arg ->
                        when (arg) {
                            is AstNode.Variable -> args[arg.name] ?: arg
                            is AstNode.Block -> arg.copy(body = expandMacros(arg.body, macros))
                            else -> arg
                        }
                    },
                    stack = invoc.stack
                )
            }
        } else {
            listOf(invoc.copy(
                args = invoc.args.map { arg ->
                    if (arg is AstNode.Block) {
                        arg.copy(body = expandMacros(arg.body, macros))
                    } else {
                        arg
                    }
                }
            ))
        }
    }
}

private const val REGISTER_VALID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"