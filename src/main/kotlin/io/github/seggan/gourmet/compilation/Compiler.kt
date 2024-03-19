package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.chef.ChefProgram
import io.github.seggan.gourmet.chef.ChefStack
import io.github.seggan.gourmet.chef.ChefStatement
import io.github.seggan.gourmet.parsing.AstNode
import java.lang.reflect.InvocationTargetException

@Suppress("unused", "UNUSED_PARAMETER")
class Compiler(private val sourceName: String, private val code: List<AstNode>) {

    private val constants = mutableMapOf<Int, Register>()
    private val allRegisters = mutableListOf<Pair<Int, String>>()
    private val tempRegisters = ArrayDeque<Register>()
    private val variables = mutableMapOf<String, Register>()

    private var registerIndex = 0

    private val instructions = mutableListOf<ChefStatement>()

    private val returnStack = ChefStack(1)
    private val argumentStack = ChefStack(2)
    private val printStack = ChefStack(3)
    private val mainStack = ChefStack(4)
    private var currentStack = mainStack
        set(value) {
            if (value.num < 4) {
                throw IllegalArgumentException("Invalid stack: $value")
            }
            field = value
        }

    private var buffered = 0

    fun compile(): ChefProgram {
        val macros = code.filterIsInstance<AstNode.Macro>()
        var expanded = code.filterIsInstance<AstNode.Invocation>()
        var lastExpanded: List<AstNode.Invocation>
        do {
            lastExpanded = expanded
            expanded = expandMacros(expanded, macros)
        } while (expanded != lastExpanded)
        compileBlock(expanded)
        if (buffered > 0) {
            throw IllegalStateException("Unflushed print buffer: $buffered")
        }
        if (instructions.lastOrNull() is ChefStatement.Clear) {
            instructions.removeLast()
        }
        return ChefProgram(sourceName, allRegisters, instructions, listOf())
    }

    private fun getNewRegister(initial: Int = 0, temp: Boolean = false): Register {
        val reg = buildString {
            var i = registerIndex++
            do {
                append(REGISTER_VALID_CHARS[i % REGISTER_VALID_CHARS.length])
                i /= REGISTER_VALID_CHARS.length
            } while (i > 0)
        }
        allRegisters += initial to reg
        return if (temp) {
            Register.Temp(reg, tempRegisters)
        } else {
            Register.Variable(reg)
        }
    }

    private fun getConstant(constant: Int): Register {
        return constants.computeIfAbsent(constant, ::getNewRegister)
    }

    private fun getTempRegister(initial: Int? = null): Register {
        val existing = tempRegisters.removeLastOrNull()
        if (existing == null || initial != null) {
            return getNewRegister(initial = initial ?: 0, temp = true)
        }
        return existing
    }

    private fun AstNode.Expression.getRegister(stack: ChefStack, copy: Boolean = false): Register {
        return when (this) {
            is AstNode.Number -> if (copy) getConstant(value) else getTempRegister(value)
            is AstNode.Register -> {
                val reg = varToRegister()
                if (copy) {
                    val temp = getTempRegister()
                    instructions += ChefStatement.Push(reg, stack)
                    instructions += ChefStatement.Pop(temp, stack)
                    temp
                } else {
                    reg
                }
            }

            is AstNode.Block -> {
                compileBlock(body)
                val reg = getTempRegister()
                instructions += ChefStatement.Pop(reg, stack)
                reg
            }

            else -> throw IllegalArgumentException("Invalid expression type: $this")
        }
    }

    private fun List<AstNode.Expression>.getRegister(stack: ChefStack, copy: Boolean = false): Register {
        if (size > 1) {
            throw IllegalArgumentException("Invalid register list: $this")
        }
        if (isEmpty()) {
            val temp = getTempRegister()
            instructions += ChefStatement.Pop(temp, stack)
            return temp
        } else {
            return get(0).getRegister(stack, copy)
        }
    }

    private fun AstNode.Expression.varToRegister(): Register {
        return if (this is AstNode.Register) {
            if (this.name == "null") {
                getTempRegister()
            } else {
                variables[name] ?: throw IllegalArgumentException("Invalid register: $name")
            }
        } else {
            throw IllegalArgumentException("Invalid expression type: $this")
        }
    }

    private fun compileBlock(block: List<AstNode.Invocation>) {
        for (insn in block) {
            try {
                Compiler::class.java.getMethod(
                    "i" + insn.name,
                    ChefStack::class.java,
                    List::class.java
                ).invoke(this@Compiler, insn.stack ?: currentStack, insn.args)
            } catch (e: NoSuchMethodException) {
                throw IllegalArgumentException("Invalid instruction: ${insn.name}")
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    //<editor-fold desc="Builtins">
    fun idef(stack: ChefStack, args: List<AstNode.Expression>) {
        val arg = args[0]
        if (arg is AstNode.Register) {
            variables[arg.name] = Register.Variable(getTempRegister().name)
        } else {
            throw IllegalArgumentException("Invalid register: $arg")
        }
    }

    fun idel(stack: ChefStack, args: List<AstNode.Expression>) {
        val arg = args[0]
        if (arg is AstNode.Register) {
            variables.remove(arg.name)?.let { tempRegisters += it }
        } else {
            throw IllegalArgumentException("Invalid register: $arg")
        }
    }

    fun iread(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.ReadNum(args[0].varToRegister())
    }

    fun ipush(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Push(args[0].getRegister(stack).also(Register::close), stack)
    }

    fun ipop(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Pop(args[0].varToRegister().also(Register::close), stack)
    }

    fun iadd(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Add(args[0].getRegister(stack).also(Register::close), stack)
    }

    fun isub(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Sub(args[0].getRegister(stack).also(Register::close), stack)
    }

    fun imul(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Mul(args[0].getRegister(stack).also(Register::close), stack)
    }

    fun idiv(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Div(args[0].getRegister(stack).also(Register::close), stack)
    }

    fun irot(stack: ChefStack, args: List<AstNode.Expression>) {
        val firstArg = args[0]
        if (firstArg is AstNode.Number) {
            instructions += ChefStatement.Rotate(stack, firstArg.value)
        } else {
            instructions += ChefStatement.RotateByReg(stack, firstArg.getRegister(stack).also(Register::close))
        }
    }

    fun ishuffle(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Randomize(stack)
    }

    fun iclear(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Clear(stack)
    }

    fun iprint(stack: ChefStack, args: List<AstNode.Expression>) {
        if (args.isEmpty()) {
            instructions += ChefStatement.Push(args.getRegister(stack).also(Register::close), printStack)
            return
        }
        for (arg in args.reversed()) {
            instructions += ChefStatement.Push(arg.getRegister(stack).also(Register::close), printStack)
            buffered++
        }
    }

    fun iflush(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Output(printStack)
        instructions += ChefStatement.Clear(printStack)
        buffered = 0
    }

    fun iflushStr(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Liquefy(printStack)
        instructions += ChefStatement.Output(printStack)
        instructions += ChefStatement.Clear(printStack)
        buffered = 0
    }

    fun ifor(stack: ChefStack, args: List<AstNode.Expression>) {
        args.dropLast(1).getRegister(stack, true).use { reg ->
            val block = args.last() as? AstNode.Block ?: throw IllegalArgumentException("Invalid block: ${args.last()}")
            instructions += ChefStatement.StartLoop(reg)
            compileBlock(block.body)
            instructions += ChefStatement.EndLoop(reg)
        }
    }

    fun iwhile(stack: ChefStack, args: List<AstNode.Expression>) {
        args.dropLast(1).getRegister(stack, true).use { reg ->
            val block = args.last() as? AstNode.Block ?: throw IllegalArgumentException("Invalid block: ${args.last()}")
            instructions += ChefStatement.StartLoop(reg)
            compileBlock(block.body)
            instructions += ChefStatement.EndLoop(null)
        }
    }

    fun ibreak(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Break
    }

    //==========================================
    fun iexec(stack: ChefStack, args: List<AstNode.Expression>) {
        val arg = args[0]
        if (arg is AstNode.Block) {
            compileBlock(arg.body)
        } else {
            throw IllegalArgumentException("Invalid block: $arg")
        }
    }
    //</editor-fold>
}

private fun expandMacros(
    code: List<AstNode.Invocation>,
    macros: List<AstNode.Macro>,
    argMap: Map<String, AstNode.Expression> = emptyMap()
): List<AstNode.Invocation> {
    return code.flatMap { invoc ->
        val mappedArgs = invoc.args.map { arg ->
            when (arg) {
                is AstNode.Variable -> argMap[arg.name] ?: arg
                is AstNode.Block -> arg.copy(body = expandMacros(arg.body, macros, argMap))
                else -> arg
            }
        }
        val macro = macros.firstOrNull { it.name == invoc.name && it.args.size == invoc.args.size }
        if (macro != null) {
            val args = macro.args.zip(mappedArgs).toMap() + argMap
            macro.body.map { invocation ->
                invocation.copy(
                    args = invocation.args.map { arg ->
                        when (arg) {
                            is AstNode.Variable -> args[arg.name] ?: arg
                            is AstNode.Block -> arg.copy(body = expandMacros(arg.body, macros, args))
                            else -> arg
                        }
                    },
                    stack = invoc.stack
                )
            }
        } else {
            listOf(invoc.copy(args = mappedArgs))
        }
    }
}

private const val REGISTER_VALID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"