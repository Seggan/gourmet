package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.*
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.typing.*
import io.github.seggan.gourmet.util.randomString

@Suppress("DuplicatedCode")
class IrGenerator private constructor(private val checked: TypedAst) {

    private val scopes = ArrayDeque<Scope>()
    private var declaredVariables = ArrayDeque<MutableSet<Variable>>()

    private val breaks = mutableListOf<BasicBlock>()
    private val continues = mutableListOf<BasicBlock>()

    private val functions = checked.functions.keys
    private val compiledFunctions = mutableListOf<CompiledFunction>()

    private val noinline = mutableSetOf<Signature>()
    private val stringLiterals = mutableListOf<String>()

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
            is AstNode.Break -> compileBreak()
            is AstNode.Continue -> compileContinue()
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
            ?: throw CompilationException("Variable not found: ${node.name}", node.extra.location)
        val trailingFields = mutableListOf<Variable>()
        val targetSize = if (node.target != null) {
            val struct = node.realType as Type.Structure
            val targetIndex = struct.fields.indexOfFirst { (name, _) -> name == node.target }
            for (i in targetIndex + 1 until struct.fields.size) {
                val field = struct.fields[i]
                val fieldVar = Variable.generate(field.first + randomString(), field.second)
                trailingFields.add(fieldVar)
                scopes.first().add(fieldVar)
                declaredVariables.first().add(fieldVar)
            }
            struct.fields[targetIndex].second.size
        } else {
            null
        }

        val op = node.assignType.op
        if (op == null) {
            if (targetSize != null) {
                +variable.push()
                if (node.isPointer) {
                    +getPointer(variable.type as Type.Pointer)
                }
                trailingFields.reversed().forEach { +it.pop() }
                +repeatN(targetSize, listOf(Insn.Pop()))
            }
            +compileExpression(node.value)
        } else {
            +variable.push()
            if (node.isPointer) {
                +getPointer(variable.type as Type.Pointer)
            }
            trailingFields.reversed().forEach { +it.pop() }
            +compileExpression(node.value)
            +op.compile()
        }
        trailingFields.forEach { +it.push() }
        if (node.isPointer) {
            +variable.push()
            +setPointer(variable.type as Type.Pointer)
        } else {
            +variable.pop()
        }
    }

    private fun compileDeclaration(node: AstNode.Declaration<TypeData>) = buildBlock {
        if (getVariable(node.name) != null) {
            throw CompilationException("Variable already declared: ${node.name}", node.extra.location)
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
        val condition = compileExpression(node.condition)
        val endBlock = buildBlock {}
        val body = compileStatement(node.body)
        doBreaksAndContinues(condition.first, endBlock.first)
        body then condition
        condition.second.continuation = Continuation.Conditional(body.first, endBlock.first)
        return body.first to endBlock.second
    }

    private fun compileWhile(node: AstNode.While<TypeData>): Blocks {
        val condition = compileExpression(node.condition)
        val endBlock = buildBlock {}
        val body = compileStatement(node.body)
        doBreaksAndContinues(condition.first, endBlock.first)
        condition.second.continuation = Continuation.Conditional(body.first, endBlock.first)
        body then condition
        return condition.first to endBlock.second
    }

    private fun compileFor(node: AstNode.For<TypeData>): Blocks {
        scopes.addFirst(Scope())
        val init = node.init?.let(::compileStatement)
        val condition = compileExpression(node.condition)
        val update = node.update?.let(::compileStatement) ?: buildBlock {}
        val endBlock = buildBlock {}
        val body = compileStatement(node.body)
        doBreaksAndContinues(update.first, endBlock.first)
        body then update then condition
        val trueEnd = BasicBlock(emptyList(), emptySet(), scopes.removeFirst().toSet())
        if (init != null) {
            init then condition
        }
        condition.second.continuation = Continuation.Conditional(body.first, trueEnd)
        return (init?.first ?: condition.first) to endBlock.second then trueEnd
    }

    private fun compileBreak(): Blocks {
        val block = buildBlock {}
        breaks.add(block.second)
        return block
    }

    private fun compileContinue(): Blocks {
        val block = buildBlock {}
        continues.add(block.second)
        return block
    }

    private fun doBreaksAndContinues(start: BasicBlock, end: BasicBlock) {
        for (breakBlock in breaks) {
            breakBlock.continuation = Continuation.Direct(end)
        }
        for (continueBlock in continues) {
            continueBlock.continuation = Continuation.Direct(start)
        }
        breaks.clear()
        continues.clear()
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
            is AstNode.MemberAccess -> compileMemberAccess(node)
            is AstNode.NumberLiteral -> buildBlock { +Insn.Push(node.value) }
            is AstNode.StringLiteral -> compileStringLiteral(node)
            is AstNode.StructInstance -> compileStructInstance(node)
            is AstNode.UnaryExpression -> buildBlock {
                +compileExpression(node.value)
                +with(node.operator) { compile(node.value.realType) }
            }

            is AstNode.Variable -> buildBlock {
                val variable = getVariable(node.name)
                    ?: throw CompilationException("Variable not found: ${node.name}", node.extra.location)
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
            throw CompilationException("Function not found: ${node.name}", node.extra.location)
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

    private fun compileStringLiteral(node: AstNode.StringLiteral<TypeData>) = buildBlock {
        val literal = node.value
        if (literal !in stringLiterals) {
            stringLiterals.add(node.value)
        }
        val ptr = stringLiterals.take(stringLiterals.indexOf(literal)).sumOf { it.length }
        +Insn.Push(ptr)
        +Insn.Push(literal.length)
    }

    private fun compileStructInstance(node: AstNode.StructInstance<TypeData>) = buildBlock {
        val type = node.realType as Type.Structure
        val values = node.values.map { it.first to compileExpression(it.second) }
        // Put them in the right order
        val sorted = values.sortedBy {
            type.fields.indexOfFirst { (name, _) -> name == it.first }
        }.unzip().second
        for (value in sorted) {
            +value
        }
    }

    private fun compileMemberAccess(node: AstNode.MemberAccess<TypeData>) = buildBlock {
        val type = node.target.realType as Type.Structure
        val fieldIndex = type.fields.indexOfFirst { (name, _) -> name == node.member }
        val field = type.fields[fieldIndex]
        val fieldSize = field.second.size
        val head = type.fields.take(fieldIndex).sumOf { it.second.size }

        val target = node.target
        if (target is AstNode.Variable) {
            val variable = getVariable(target.name)
                ?: throw CompilationException("Variable not found: ${target.name}", target.extra.location)
            val parts = variable.mapped.subList(head, head + fieldSize)
            for (part in parts) {
                +Insn("push", Argument.Variable(part))
            }
        } else {
            val tail = type.fields.drop(fieldIndex + 1).sumOf { it.second.size }
            +compileExpression(node.target)
            repeat(tail) {
                +Insn.Pop()
            }
            +repeatN(fieldSize, listOf(Insn("rot", Argument.Number(head + fieldSize - 1))))
            repeat(head) {
                +Insn.Pop()
            }
        }
    }

    fun getPointer(pointer: Type.Pointer) = buildBlock {
        val baseType = pointer.target
        val ptr = Argument.Variable("ptr")
        val tempDeref = Argument.Variable("tempDeref")
        +Insn("def", ptr)
        +Insn("pop", ptr)
        +Insn("def", tempDeref)
        +Insn("push", ARG_HEAP_SIZE)
        +Insn("sub", ptr)
        +Insn("del", ptr)
        val moved = Argument.Variable("moved")
        +Insn("def", moved)
        +Insn("clone", moved)
        +Insn(
            "for", Argument.Block(
                Insn("pop", tempDeref, stack = STACK_HEAP),
                Insn("push", tempDeref, stack = STACK_ANTI_HEAP)
            )
        )
        +repeatN(
            baseType.size, listOf(
                Insn("pop", tempDeref, stack = STACK_ANTI_HEAP),
                Insn("push", tempDeref),
                Insn("push", tempDeref, stack = STACK_HEAP)
            )
        )
        +Insn("push", moved)
        +Insn("sub", Argument.Number(baseType.size))
        +Insn(
            "for", Argument.Block(
                Insn("pop", tempDeref, stack = STACK_ANTI_HEAP),
                Insn("push", tempDeref, stack = STACK_HEAP),
            )
        )
        +Insn("del", moved)
        +Insn("del", tempDeref)
    }

    private fun setPointer(pointer: Type.Pointer) = buildBlock {
        val baseType = pointer.target
        val ptr = Argument.Variable("ptr")
        val tempRef = Argument.Variable("tempRef")
        +Insn("def", ptr)
        +Insn("pop", ptr)
        +Insn("def", tempRef)
        +Insn("push", ARG_HEAP_SIZE)
        +Insn("sub", ptr)
        +Insn("del", ptr)
        +Insn("sub", Argument.Number(baseType.size))
        val moved = Argument.Variable("moved")
        +Insn("def", moved)
        +Insn("clone", moved)
        +Insn(
            "for", Argument.Block(
                Insn("pop", tempRef, stack = STACK_HEAP),
                Insn("push", tempRef, stack = STACK_ANTI_HEAP)
            )
        )
        +repeatN(
            baseType.size, listOf(
                Insn("pop", tempRef, stack = STACK_HEAP),
                Insn("pop", tempRef),
                Insn("push", tempRef, stack = STACK_ANTI_HEAP)
            )
        )
        +Insn("push", moved)
        +Insn("add", Argument.Number(baseType.size))
        +Insn(
            "for", Argument.Block(
                Insn("pop", tempRef, stack = STACK_ANTI_HEAP),
                Insn("push", tempRef, stack = STACK_HEAP),
            )
        )
        +Insn("del", moved)
        +Insn("del", tempRef)
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

    private fun repeatN(n: Int, insns: List<Insn>): List<Insn> = buildList {
        if (n == 0) {
            return emptyList()
        } else if (n <= insns.size * n + 3) {
            repeat(n) { addAll(insns) }
        } else {
            add(Insn.Push(n))
            add(Insn("for", Argument.Block(insns)))
        }
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

        private val ARG_HEAP_SIZE = Argument.Variable("heapSize")
        private const val STACK_HEAP = "heap"
        private const val STACK_ANTI_HEAP = "antiHeap"

        fun generate(
            checked: TypedAst,
        ): Pair<List<CompiledFunction>, List<String>> {
            val generator = IrGenerator(checked)
            return generator.compile() to generator.stringLiterals
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