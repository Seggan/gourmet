package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.CompiledFunction
import io.github.seggan.gourmet.compilation.ir.Continuation
import io.github.seggan.gourmet.compilation.ir.Insn
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.typing.*

class IrGenerator private constructor(private val checked: TypedAst) {

    private val scopes = ArrayDeque<Scope>()
    private var declaredVariables = ArrayDeque<MutableSet<Variable>>()

    private val functions = checked.functions.keys
    private val compiledFunctions = mutableListOf<CompiledFunction>()

    private val noinline = mutableSetOf<Signature>()

    private fun compile(): List<CompiledFunction> {
        checked.functions.values.forEach(::compileFunction)
        return compiledFunctions.filter {
            "entry" in it.attributes || it.signature in noinline
        }
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
        val blocks = block.statements.fold(buildBlock {}) { acc, statement ->
            acc then compileStatement(statement)
        }
        val dropped = scopes.removeFirst().toSet()
        val tail = BasicBlock(emptyList(), emptySet(), dropped)
        return blocks then tail
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
            is AstNode.DoWhile -> compileDoWhile(node)
            is AstNode.For -> compileFor(node)
            is AstNode.If -> compileIf(node)
            is AstNode.Return -> compileReturn(node)
            is AstNode.While -> compileWhile(node)
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
        declaredVariables.first().add(variable)
        if (node.value != null) {
            +compileExpression(node.value)
            +variable.pop()
        }
        scopes.first().add(variable)
    }

    private fun compileIf(node: AstNode.If<TypeData>): Blocks {
        val condition = compileExpression(node.condition)
        val thenBlock = compileStatement(node.thenBody)
        val elseBlock = node.elseBody?.let(::compileStatement)
        val endBlock = buildBlock {}
        condition.second.continuation = Continuation.Conditional(
            thenBlock.first,
            elseBlock?.first ?: endBlock.first
        )
        thenBlock then endBlock
        if (elseBlock != null) {
            elseBlock then endBlock
        }
        return condition.first to endBlock.second
    }

    private fun compileDoWhile(node: AstNode.DoWhile<TypeData>): Blocks {
        val body = compileStatement(node.body)
        val condition = compileExpression(node.condition)
        val endBlock = BasicBlock(emptyList(), emptySet(), emptySet())
        body then condition
        condition.second.continuation = Continuation.Conditional(body.first, endBlock)
        return body.first to endBlock
    }

    private fun compileWhile(node: AstNode.While<TypeData>): Blocks {
        val condition = compileExpression(node.condition)
        val body = compileStatement(node.body)
        val endBlock = buildBlock {}
        condition.second.continuation = Continuation.Conditional(body.first, endBlock.first)
        body then condition
        return condition.first to endBlock.second
    }

    private fun compileFor(node: AstNode.For<TypeData>): Blocks {
        scopes.addFirst(Scope())
        val init = node.init?.let(::compileStatement)
        val condition = compileExpression(node.condition)
        val update = node.update?.let(::compileStatement)
        val body = compileStatement(node.body)
        val endBlock = BasicBlock(emptyList(), emptySet(), scopes.removeFirst().toSet())
        if (init != null) {
            init then condition
        }
        condition.second.continuation = Continuation.Conditional(body.first, endBlock)
        if (update != null) {
            body then update then condition
        } else {
            body then condition
        }
        return (init?.first ?: condition.first) to endBlock
    }

    private fun compileReturn(node: AstNode.Return<TypeData>): Blocks {
        val block = buildBlock {
            if (node.value != null) {
                +compileExpression(node.value)
            } else {
                +Insn.Push(0)
            }
        }
        val dropBlock = BasicBlock(emptyList(), emptySet(), scopes.flatten().toSet(), Continuation.Return)
        block.second.continuation = Continuation.Direct(dropBlock)
        return block.first to dropBlock
    }

    fun compileExpression(node: AstNode.Expression<TypeData>): Blocks {
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
            is AstNode.UnaryExpression -> buildBlock {
                +compileExpression(node.value)
                +node.operator.compile()
            }

            is AstNode.Variable -> buildBlock {
                val variable = getVariable(node.name)
                    ?: throw CompilationException("Variable not found: ${node.name}", node.location)
                +variable.push()
            }
        }
    }

    private fun compileCall(node: AstNode.FunctionCall<TypeData>): Blocks {
        val typeData = node.extra as TypeData.FunctionCall
        val compiletime = CompiletimeFunction.signatures[typeData.overload]
        if (compiletime != null) {
            return with(compiletime) { compile(node) }
        }
        val signature = typeData.overload.copy(type = typeData.call)
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

    fun getVariable(name: String): Variable? {
        for (scope in scopes) {
            val variable = scope.find { it.name == name }
            if (variable != null) {
                return variable
            }
        }
        return null
    }

    inner class BlockBuilder {
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
                emptySet()
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

    inline fun buildBlock(init: BlockBuilder.() -> Unit): Blocks {
        return BlockBuilder().apply(init).build()
    }

    companion object {
        fun generate(
            checked: TypedAst,
        ): List<CompiledFunction> {
            return IrGenerator(checked, ).compile()
        }
    }
}

typealias Blocks = Pair<BasicBlock, BasicBlock>

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