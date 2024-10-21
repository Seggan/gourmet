package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.CompiledFunction
import io.github.seggan.gourmet.compilation.ir.Continuation
import io.github.seggan.gourmet.compilation.ir.Insn
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.parsing.UnOp
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeData
import io.github.seggan.gourmet.typing.realType

class IrGenerator private constructor() {

    private val scopes = ArrayDeque<Scope>()
    private var declaredVariables = ArrayDeque<MutableSet<Variable>>()

    private fun compile(ast: AstNode.File<TypeData>): List<CompiledFunction> {
        return ast.functions.map(::compileFunction)
    }

    private fun compileFunction(function: AstNode.Function<TypeData>): CompiledFunction {
        val ftype = function.realType as Type.Function
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
        return CompiledFunction(function.attributes, function.name, ftype, head.first)
    }

    private fun compileBlock(block: AstNode.Block<TypeData>, newScope: Boolean = true): Blocks {
        if (newScope) scopes.addFirst(Scope())
        val blocks = compileStatements(block.statements)
        val dropped = scopes.removeFirst().toSet()
        val tail = BasicBlock(emptyList(), mutableSetOf(), dropped, blocks.second.continuation)
        blocks.second.continuation = null
        return blocks then tail
    }

    private fun compileStatements(statements: AstNode.Statements<TypeData>): Blocks {
        return statements.statements.map(::compileStatement).reduce(Blocks::then)
    }

    private fun compileStatement(statement: AstNode.Statement<TypeData>): Blocks {
        return when (statement) {
            is AstNode.Expression -> buildBlock {
                +compileExpression(statement)
                repeat(statement.realType.size) {
                    +Insn.Pop()
                }
            }
            is AstNode.Assignment -> compileAssignment(statement)
            is AstNode.Block -> compileBlock(statement)
            is AstNode.Declaration -> compileDeclaration(statement)
            is AstNode.DoWhile -> TODO()
            is AstNode.If -> TODO()
            is AstNode.Return -> compileReturn(statement)
            is AstNode.Statements -> compileStatements(statement)
            is AstNode.While -> TODO()
        }
    }

    private fun compileAssignment(node: AstNode.Assignment<TypeData>) = buildBlock {
        val variable = getVariable(node.name)
            ?: throw CompilationException("Variable not found: ${node.name}", node.location)
        +compileExpression(node.value)
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

    private fun compileExpression(expression: AstNode.Expression<TypeData>): Blocks {
        return when (expression) {
            is AstNode.BinaryExpression -> buildBlock {
                +compileExpression(expression.left)
                +compileExpression(expression.right)
                +expression.operator.compile()
            }

            is AstNode.BooleanLiteral -> buildBlock { +Insn.Push(if (expression.value) 1 else 0) }
            is AstNode.CharLiteral -> buildBlock { +Insn.Push(expression.value.code) }
            is AstNode.FunctionCall -> TODO()
            is AstNode.MemberAccess -> TODO()
            is AstNode.NumberLiteral -> buildBlock { +Insn.Push(expression.value) }
            is AstNode.StringLiteral -> TODO()
            is AstNode.UnaryExpression -> when (expression.operator) {
                UnOp.ASM -> compileAsm(expression)
                UnOp.SIZEOF -> compileSizeof(expression)
                else -> buildBlock {
                    +compileExpression(expression.value)
                    +expression.operator.compile()
                }
            }
            is AstNode.Variable -> buildBlock {
                val variable = getVariable(expression.name)
                    ?: throw CompilationException("Variable not found: ${expression.name}", expression.location)
                +variable.push()
            }
        }
    }

    private fun compileAsm(expression: AstNode.UnaryExpression<TypeData>) = buildBlock {
        val literal = expression.value
        if (literal !is AstNode.StringLiteral) {
            throw CompilationException("asm requires a string literal", expression.location)
        }
        val value = literal.value
        val mangled = variableReplacementRegex.replace(value) {
            val name = it.groupValues[1]
            val part = it.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val variable = getVariable(name)
                ?: throw CompilationException("Variable not found: $name", expression.location)
            '$' + variable.mapped[part]
        }
        +Insn.raw(mangled)
    }

    private fun compileSizeof(expression: AstNode.UnaryExpression<TypeData>) = buildBlock {
        +Insn.Push(expression.value.realType.size)
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

    companion object {
        fun generate(ast: AstNode.File<TypeData>): List<CompiledFunction> {
            return IrGenerator().compile(ast)
        }
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
                declaredVariables.removeFirst(),
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