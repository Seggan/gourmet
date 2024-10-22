package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.CompiledFunction
import io.github.seggan.gourmet.compilation.ir.Continuation
import io.github.seggan.gourmet.compilation.ir.Insn
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.parsing.UnOp
import io.github.seggan.gourmet.typing.Signature
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeData
import io.github.seggan.gourmet.typing.realType

class IrGenerator private constructor(
    private val ast: AstNode.File<TypeData>,
    private val compiledFunctions: MutableList<CompiledFunction>
) {

    private val scopes = ArrayDeque<Scope>()
    private var declaredVariables = ArrayDeque<MutableSet<Variable>>()

    private val functions = ast.functions.map {
        Signature(it.name, it.realType as Type.Function)
    }.toSet() + compiledFunctions.map { it.signature }

    private val noinline = mutableSetOf<Signature>()

    private fun compile(removeUnused: Boolean): List<CompiledFunction> {
        ast.functions.forEach(::compileFunction)
        return if (removeUnused) compiledFunctions.filter { "entry" in it.attributes || it.signature in noinline }
        else compiledFunctions
    }

    private fun compileFunction(function: AstNode.Function<TypeData>) {
        val ftype = function.realType as Type.Function
        val signature = Signature(function.name, ftype)
        if ("inline" in function.attributes && signature in noinline) {
            System.err.println("Warning: inline function ${function.name} used before declaration; not inlining")
        }
        val scope = Scope()
        scopes.addFirst(scope)
        val head = buildBlock {
            val names = function.args.map { it.first }
            for ((name, type) in names.zip(ftype.args).reversed()) {
                val variable = Variable.generate(name, type)
                scope.add(variable)
                declaredVariables.first().add(variable)
                +variable.pop()
            }
        }
        val body = compileBlock(function.body, false)
        head then body
        compiledFunctions.add(CompiledFunction(signature, function.attributes, head.first))
    }

    private fun compileBlock(block: AstNode.Block<TypeData>, newScope: Boolean = true): Blocks {
        if (newScope) scopes.addFirst(Scope())
        val blocks = compileStatements(block.statements)
        val dropped = scopes.removeFirst().toSet()
        val tail = BasicBlock(emptyList(), mutableSetOf(), dropped, blocks.second.continuation)
        blocks.second.continuation = null
        return blocks then tail
    }

    private fun compileStatements(node: AstNode.Statements<TypeData>): Blocks {
        return node.statements.map(::compileStatement).reduce(Blocks::then)
    }

    private fun compileStatement(node: AstNode.Statement<TypeData>): Blocks {
        return when (node) {
            is AstNode.Expression -> buildBlock {
                +compileExpression(node)
                repeat(node.realType.size) {
                    +Insn.Pop()
                }
            }

            is AstNode.Assignment -> compileAssignment(node)
            is AstNode.Block -> compileBlock(node)
            is AstNode.Declaration -> compileDeclaration(node)
            is AstNode.DoWhile -> TODO()
            is AstNode.If -> TODO()
            is AstNode.Return -> compileReturn(node)
            is AstNode.Statements -> compileStatements(node)
            is AstNode.While -> TODO()
        }
    }

    private fun compileAssignment(node: AstNode.Assignment<TypeData>) = buildBlock {
        val variable = getVariable(node.name)
            ?: throw CompilationException("Variable not found: ${node.name}", node.location)
        val op = node.assignType.op
        if (op == null) {
            +compileExpression(node.value)
        } else {
            +variable.push()
            +compileExpression(node.value)
            +op.compile()
        }
        +variable.pop()
    }

    private fun compileDeclaration(node: AstNode.Declaration<TypeData>) = buildBlock {
        if (getVariable(node.name) != null) {
            throw CompilationException("Variable already declared: ${node.name}", node.location)
        }
        val variable = Variable.generate(node.name, node.realType)
        scopes.first().add(variable)
        declaredVariables.first().add(variable)
        if (node.value != null) {
            +compileExpression(node.value)
            +variable.pop()
        }
    }

    private fun compileReturn(node: AstNode.Return<TypeData>): Blocks {
        val block = buildBlock { /* TODO */ }
        block.second.continuation = Continuation.Return
        return block
    }

    private fun compileExpression(node: AstNode.Expression<TypeData>): Blocks {
        return when (node) {
            is AstNode.BinaryExpression -> buildBlock {
                +compileExpression(node.left)
                +compileExpression(node.right)
                +node.operator.compile()
            }

            is AstNode.BooleanLiteral -> buildBlock { +Insn.Push(if (node.value) 1 else 0) }
            is AstNode.CharLiteral -> buildBlock { +Insn.Push(node.value.code) }
            is AstNode.FunctionCall -> compileCall(node)
            is AstNode.MemberAccess -> TODO()
            is AstNode.NumberLiteral -> buildBlock { +Insn.Push(node.value) }
            is AstNode.StringLiteral -> TODO()
            is AstNode.UnaryExpression -> when (node.operator) {
                UnOp.ASM -> compileAsm(node)
                UnOp.SIZEOF -> compileSizeof(node)
                else -> buildBlock {
                    +compileExpression(node.value)
                    +node.operator.compile()
                }
            }

            is AstNode.Variable -> buildBlock {
                val variable = getVariable(node.name)
                    ?: throw CompilationException("Variable not found: ${node.name}", node.location)
                +variable.push()
            }
        }
    }

    private fun compileAsm(node: AstNode.UnaryExpression<TypeData>) = buildBlock {
        val literal = node.value
        if (literal !is AstNode.StringLiteral) {
            throw CompilationException("asm requires a string literal", node.location)
        }
        val value = literal.value
        val mangled = variableReplacementRegex.replace(value) {
            val name = it.groupValues[1]
            val part = it.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val variable = getVariable(name)
                ?: throw CompilationException("Variable not found: $name", node.location)
            '$' + variable.mapped[part]
        }
        +Insn.raw(mangled)
    }

    private fun compileSizeof(node: AstNode.UnaryExpression<TypeData>) = buildBlock {
        +Insn.Push(node.value.realType.size)
    }

    private fun compileCall(node: AstNode.FunctionCall<TypeData>): Blocks {
        val signature = (node.extra as TypeData.FunctionCall).overload
        if (signature !in functions) {
            throw CompilationException("Function not found: ${node.name}", node.location)
        }
        val argBlock = buildBlock {
            for (arg in node.args) {
                +compileExpression(arg)
            }
        }
        val compiled = compiledFunctions.firstOrNull { it.signature == signature }
        if (compiled != null && "inline" in compiled.attributes) {
            val endBlock = buildBlock {}
            val body = compiled.body.clone()
            for (child in body.children) {
                if (child.continuation is Continuation.Return) {
                    child.continuation = Continuation.Direct(endBlock.first)
                }
            }
            argBlock.second.continuation = Continuation.Direct(body)
            return argBlock.first to endBlock.second
        } else {
            noinline.add(signature)
            val callBlock = argBlock then buildBlock {
                for (variable in scopes.flatten()) {
                    +variable.push("callStack")
                }
            }
            val restoreBlock = buildBlock {
                for (variable in scopes.flatten().reversed()) {
                    +variable.pop("callStack")
                }
            }
            callBlock.second.continuation = Continuation.Call(signature, restoreBlock.first)
            return callBlock.first to restoreBlock.second
        }
    }

    private fun getVariable(name: String): Variable? {
        for (scope in scopes) {
            val variable = scope.find { it.name == name }
            if (variable != null) {
                return variable
            }
        }
        return null
    }

    private inner class BlockBuilder {
        private var insns = mutableListOf<Insn>()

        private lateinit var blocks: Blocks

        init {
            declaredVariables.addFirst(mutableSetOf())
        }

        operator fun Insn.unaryPlus() {
            insns.add(this)
        }

        operator fun List<Insn>.unaryPlus() {
            insns.addAll(this)
        }

        operator fun Blocks.unaryPlus() {
            popBlock()
            blocks = blocks then this
        }

        private fun popBlock() {
            val block = BasicBlock(
                insns,
                declaredVariables.removeFirst().toSet(),
                mutableSetOf()
            )
            declaredVariables.addFirst(mutableSetOf())
            blocks = if (::blocks.isInitialized) {
                blocks then block
            } else {
                block to block
            }
            insns = mutableListOf()
        }

        fun build(): Blocks {
            popBlock()
            declaredVariables.removeFirst()
            return blocks
        }
    }

    private inline fun buildBlock(init: BlockBuilder.() -> Unit): Blocks {
        return BlockBuilder().apply(init).build()
    }

    companion object {
        fun generate(
            ast: AstNode.File<TypeData>,
            external: List<CompiledFunction> = emptyList(),
            removeUnused: Boolean = true
        ): List<CompiledFunction> {
            return IrGenerator(ast, external.toMutableList()).compile(removeUnused)
        }
    }
}

private typealias Blocks = Pair<BasicBlock, BasicBlock>

private infix fun Blocks.then(block: BasicBlock): Blocks {
    if (second.continuation == null) {
        second.continuation = Continuation.Direct(block)
        return first to block
    } else {
        return this
    }
}

private infix fun Blocks.then(blocks: Blocks): Blocks {
    if (second.continuation == null) {
        second.continuation = Continuation.Direct(blocks.first)
        return first to blocks.second
    } else {
        return this
    }
}

private val variableReplacementRegex = Regex("""\[([a-zA-Z_][a-zA-Z0-9_]*)(?::(\d+))?]""")