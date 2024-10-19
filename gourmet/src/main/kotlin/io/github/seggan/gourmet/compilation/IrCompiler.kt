package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.*
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeData
import io.github.seggan.gourmet.typing.realType

class IrCompiler private constructor(private val ast: AstNode.File<TypeData>) {

    private val scopes = ArrayDeque<Scope>()
    private var declaredVariables = ArrayDeque<MutableSet<Variable>>()
    private var outsideVariables = ArrayDeque<MutableSet<Variable>>()

    private fun compile(): CompiledBlocks {
        return CompiledBlocks(ast.functions.associate(::compileFunction))
    }

    private fun compileFunction(function: AstNode.Function<TypeData>): Pair<Type.Function, BasicBlock> {
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
        return ftype to head.first
    }

    private fun compileBlock(block: AstNode.Block<TypeData>, newScope: Boolean = true): Blocks {
        if (newScope) scopes.addFirst(Scope())
        val blocks = compileStatements(block.statements)
        val dropped = scopes.removeFirst().toSet()
        val tail = BasicBlock(emptyList(), mutableSetOf(), dropped, mutableSetOf(), blocks.second.continuation)
        blocks.second.continuation = null
        return blocks then tail
    }

    private fun compileStatements(statements: AstNode.Statements<TypeData>): Blocks {
        return statements.statements.map(::compileStatement).reduce(Blocks::then)
    }

    private fun compileStatement(statement: AstNode.Statement<TypeData>): Blocks {
        return when (statement) {
            is AstNode.Expression -> compileExpression(statement)
            is AstNode.Assignment -> compileAssignment(statement)
            is AstNode.Block -> compileBlock(statement)
            is AstNode.Declaration -> compileDeclaration(statement)
            is AstNode.DoWhile -> TODO()
            is AstNode.If -> TODO()
            is AstNode.Return -> TODO()
            is AstNode.Statements -> compileStatements(statement)
            is AstNode.While -> TODO()
        }
    }

    private fun compileAssignment(assignment: AstNode.Assignment<TypeData>) = buildBlock {
        val variable = getVariable(assignment.name)
            ?: throw CompilationException("Variable not found: ${assignment.name}")
        +compileExpression(assignment.value)
        +variable.pop()
    }

    private fun compileDeclaration(declaration: AstNode.Declaration<TypeData>) = buildBlock {
        if (getVariable(declaration.name) != null) {
            throw CompilationException("Variable already declared: ${declaration.name}")
        }
        val variable = Variable.generate(declaration.name, declaration.realType)
        scopes.first().add(variable)
        declaredVariables.first().add(variable)
        if (declaration.value != null) {
            +compileExpression(declaration.value)
            +variable.pop()
        }
    }

    private fun compileDoWhile(doWhile: AstNode.DoWhile<TypeData>): Blocks {
        TODO()
    }

    private fun compileExpression(expression: AstNode.Expression<TypeData>): Blocks {
        return when (expression) {
            is AstNode.BinaryExpression -> TODO()
            is AstNode.BooleanLiteral -> TODO()
            is AstNode.CharLiteral -> TODO()
            is AstNode.FunctionCall -> TODO()
            is AstNode.MemberAccess -> TODO()
            is AstNode.NumberLiteral -> buildBlock { +Instruction("push", Argument.Number(expression.value)) }
            is AstNode.StringLiteral -> TODO()
            is AstNode.UnaryExpression -> TODO()
            is AstNode.Variable -> TODO()
        }
    }

    private fun getVariable(name: String): Variable? {
        for (scope in scopes) {
            val variable = scope.find { it.name == name }
            if (variable != null) {
                if (scope != scopes.first()) {
                    outsideVariables.first().add(variable)
                }
                return variable
            }
        }
        return null
    }

    private fun Variable.push(stack: String? = null): List<Instruction> {
        if (this !in scopes.first()) {
            outsideVariables.first().add(this)
        }
        return mapped.map { Instruction("push", Argument.Variable(it), stack = stack) }
    }

    private fun Variable.pop(stack: String? = null): List<Instruction> {
        if (this !in scopes.first()) {
            outsideVariables.first().add(this)
        }
        return mapped.reversed().map { Instruction("pop", Argument.Variable(it), stack = stack) }
    }

    companion object {
        fun compile(ast: AstNode.File<TypeData>): CompiledBlocks {
            return IrCompiler(ast).compile()
        }
    }

    private inner class BlockBuilder {
        private var insns = mutableListOf<Instruction>()

        private lateinit var blocks: Blocks

        init {
            declaredVariables.addFirst(mutableSetOf())
            outsideVariables.addFirst(mutableSetOf())
        }

        operator fun Instruction.unaryPlus() {
            insns.add(this)
        }

        operator fun List<Instruction>.unaryPlus() {
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
                mutableSetOf(),
                outsideVariables.removeFirst()
            )
            declaredVariables.addFirst(mutableSetOf())
            outsideVariables.addFirst(mutableSetOf())
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
            outsideVariables.removeFirst()
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

class CompilationException(message: String) : Exception(message)