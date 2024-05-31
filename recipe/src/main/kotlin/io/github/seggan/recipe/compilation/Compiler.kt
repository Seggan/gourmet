package io.github.seggan.recipe.compilation

import io.github.seggan.recipe.chef.ChefProgram
import io.github.seggan.recipe.chef.ChefStack
import io.github.seggan.recipe.chef.ChefStatement
import io.github.seggan.recipe.parsing.AstNode
import io.github.seggan.recipe.parsing.flatten
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("unused", "UNUSED_PARAMETER")
class Compiler(private val sourceName: String, private val code: List<AstNode>, private val debug: Boolean = false) {

    private val constants = mutableMapOf<BigDecimal, Register>()
    private val allRegisters = mutableListOf<Pair<BigInteger?, String>>()
    private val tempRegisters = ArrayDeque<Register>()
    private val variables = mutableMapOf<String, Register>()

    private var registerIndex = 0

    private val instructions = mutableListOf<ChefStatement>()

    private val returnStack = ChefStack(1)
    private val argumentStack = ChefStack(2)
    private val printStack = ChefStack(3)
    private val mainStack = if (code.any { it is AstNode.Function }) ChefStack(4) else returnStack
    private var currentStack = mainStack
    private val stacks = mutableMapOf(
        "_return" to returnStack,
        "_args" to argumentStack,
        "_print" to printStack,
        "_main" to mainStack
    )

    private val functionsReturn = mutableMapOf<String, Boolean>()

    private var buffered = 0

    fun compile(): ChefProgram {
        val macros = code.filterIsInstance<AstNode.Macro>()
        val functions = code.filterIsInstance<AstNode.Function>().map {
            val compiler = Compiler(it.name, it.body, debug)
            compiler.instructions += ChefStatement.Clear(compiler.returnStack)
            for (arg in it.args.reversed()) {
                val reg = Register.Variable(compiler.getTempRegister().name)
                compiler.variables[arg] = reg
                compiler.instructions += ChefStatement.Pop(reg, argumentStack)
            }
            compiler.compile()
        }
        for (function in functions) {
            functionsReturn[function.name] = function.steps.any { it is ChefStatement.Return }
        }
        var expanded = code.filterIsInstance<AstNode.Invocation>()
        var lastExpanded: List<AstNode.Invocation>
        do {
            lastExpanded = expanded
            expanded = expandMacros(expanded, macros)
        } while (expanded != lastExpanded)
        functionsReturn[sourceName] = expanded.flatMap(AstNode::flatten).any { it is AstNode.Invocation && it.name == "return"}
        compileBlock(expanded)
        if (buffered > 0) {
            throw IllegalStateException("Unflushed print buffer: $buffered")
        }
        if (instructions.lastOrNull() is ChefStatement.Clear) {
            instructions.removeLast()
        }
        return ChefProgram(sourceName, allRegisters, instructions, functions)
    }

    private fun getNewRegister(initial: BigInteger?, temp: Boolean = false): Register {
        val reg = buildString {
            var i = registerIndex++
            do {
                append(REGISTER_VALID_CHARS[i % REGISTER_VALID_CHARS.length])
                i /= REGISTER_VALID_CHARS.length
            } while (i > 0)
        }
        if (reg in VALID_MEASURES) {
            return getNewRegister(initial, temp)
        }
        allRegisters += initial to reg
        return if (temp) {
            Register.Temp(reg, tempRegisters)
        } else {
            Register.Variable(reg)
        }
    }

    private fun getConstant(constant: BigDecimal): Register {
        return constants.getOrPut(constant) {
            val needsExtra = extraNumberSteps(constant)
            val reg = getNewRegister(if (needsExtra) null else constant.toBigIntegerExact())
            if (needsExtra) {
                instructions += ChefStatement.Pop(reg, currentStack)
            }
            reg
        }
    }

    private fun getTempRegister(initial: BigDecimal? = null): Register {
        val needPop = initial != null && extraNumberSteps(initial)
        val reg = tempRegisters.removeLastOrNull() ?: return getNewRegister(
            if (needPop) null else initial?.toBigIntegerExact(),
            temp = true
        )
        if (needPop) {
            instructions += ChefStatement.Pop(reg, currentStack)
        }
        return reg
    }

    private fun extraNumberSteps(num: BigDecimal): Boolean {
        if (num.isInteger() && num.signum() >= 0) return false
        if (num.signum() < 0) {
            instructions += ChefStatement.Push(getConstant(BigDecimal.ZERO), currentStack)
            instructions += ChefStatement.Sub(getConstant(num.negate()), currentStack)
        } else {
            // Decimal number
            val neum = num.unscaledValue()
            val denom = BigInteger.TEN.pow(num.scale())
            val gcd = neum.gcd(denom)
            instructions += ChefStatement.Push(getConstant(BigDecimal(neum / gcd)), currentStack)
            instructions += ChefStatement.Div(getConstant(BigDecimal(denom / gcd)), currentStack)
        }
        return true
    }

    private fun AstNode.Expression.getRegister(stack: ChefStack, copy: Boolean = false): Register {
        return when (this) {
            is AstNode.Number -> if (copy) getTempRegister(value) else getConstant(value)
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
                ).invoke(
                    this@Compiler,
                    insn.stack?.let { stacks[it.name] } ?: currentStack,
                    insn.args
                )
            } catch (e: NoSuchMethodException) {
                throw IllegalArgumentException("Invalid instruction: ${insn.name}")
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    //<editor-fold desc="Builtins">
    fun idef(stack: ChefStack, args: List<AstNode.Expression>) {
        when (val arg = args[0]) {
            is AstNode.Register -> variables[arg.name] = Register.Variable(getTempRegister().name)
            is AstNode.Stack -> {
                var idx = 5
                val used = stacks.values.map { it.num }.toSet()
                while (idx in used) {
                    idx++
                }
                stacks[arg.name] = ChefStack(idx)
            }

            else -> throw IllegalArgumentException("Invalid register: $arg")
        }
    }

    fun idel(stack: ChefStack, args: List<AstNode.Expression>) {
        when (val arg = args[0]) {
            is AstNode.Register -> variables.remove(arg.name)?.let { tempRegisters += it }
            is AstNode.Stack -> stacks.remove(arg.name)?.let { instructions += ChefStatement.Clear(it) }
            else -> throw IllegalArgumentException("Invalid register: $arg")
        }
    }

    fun iundef(stack: ChefStack, args: List<AstNode.Expression>) {
        when (val arg = args[0]) {
            is AstNode.Register -> variables.remove(arg.name)
            is AstNode.Stack -> stacks.remove(arg.name)
            else -> throw IllegalArgumentException("Invalid register: $arg")
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
            instructions += ChefStatement.Rotate(stack, firstArg.value.toInt())
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
        args.dropLast(1).getRegister(stack).use { reg ->
            val block = args.last() as? AstNode.Block ?: throw IllegalArgumentException("Invalid block: ${args.last()}")
            instructions += ChefStatement.StartLoop(reg)
            compileBlock(block.body)
            instructions += ChefStatement.EndLoop(null)
        }
    }

    fun ibreak(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Break
    }

    fun icall(stack: ChefStack, args: List<AstNode.Expression>) {
        val name = (args[0] as? AstNode.Register)?.name ?: throw IllegalArgumentException("Invalid function: ${args[0]}")
        val doesReturn = functionsReturn[name] ?: throw IllegalArgumentException("Unknown function: $name")
        for (arg in args.drop(1)) {
            instructions += ChefStatement.Push(arg.getRegister(stack).also(Register::close), argumentStack)
        }
        instructions += ChefStatement.Call(name)
        if (doesReturn) {
            getTempRegister().use {
                instructions += ChefStatement.Pop(it, returnStack)
                instructions += ChefStatement.Push(it, stack)
            }
        }
    }

    fun ireturn(stack: ChefStack, args: List<AstNode.Expression>) {
        instructions += ChefStatement.Push(args.getRegister(stack).also(Register::close), returnStack)
        instructions += ChefStatement.Return
    }

    //==========================================
    fun iexec(stack: ChefStack, args: List<AstNode.Expression>) {
        val arg = args[0]
        if (arg is AstNode.Block) {
            val oldCurrent = currentStack
            currentStack = stack
            compileBlock(arg.body)
            currentStack = oldCurrent
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
                    stack = invocation.stack ?: invoc.stack
                )
            }
        } else {
            listOf(invoc.copy(args = mappedArgs))
        }
    }
}

private const val REGISTER_VALID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
private val VALID_MEASURES = setOf(
    "g",
    "kg",
    "pinch",
    "pinches",
    "ml",
    "l",
    "dash",
    "dashes",
    "cup",
    "cups",
    "teaspoon",
    "teaspoons",
    "tablespoon",
    "tablespoons"
)

fun BigDecimal.isInteger() = signum() == 0 || scale() <= 0 || stripTrailingZeros().scale() <= 0