package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.parsing.*
import io.github.seggan.gourmet.typing.Type
import kotlin.random.Random
import kotlin.random.nextULong

class Compiler(private val file: AstNode.File<Type>) {

    private val code = StringBuilder()
    private val blocks = mutableListOf<String>()
    private val scopes = ArrayDeque<MutableList<Variable>>().apply { addLast(mutableListOf()) }

    private val allVars = mutableSetOf<String>()
    private val functionEntries = mutableMapOf<String, Int>()
    private val functions = mutableMapOf<String, Type.Function>()

    private val stackStack = "@stack${randomString()}"
    val stateVar = "\$state${randomString()}"

    private val heapStack = "@heap${randomString()}"

    fun compile(): String {
        file.functions.forEach { functions[it.name] = it.extra as Type.Function }
        file.functions.forEach(::compileFunction)
        if (code.isNotEmpty()) {
            blocks.add(code.toString())
        }
        val main = StringBuilder()
        for (varName in allVars) {
            main.appendLine("def $varName;")
        }
        main.appendLine("def $stackStack;")
        main.appendLine("def $heapStack;")

        val mainEntry = functionEntries["main"] ?: throw CompilationException("Main function not found")
        main.appendLine("def $stateVar $mainEntry;")
        main.appendLine("while 1 {")
        for ((i, block) in blocks.withIndex()) {
            main.appendLine("push $i;")
            main.appendLine("eq $stateVar;")
            main.appendLine("if {")
            main.appendLine(block)
            main.appendLine("};")
        }
        main.appendLine("};")
        return main.toString()
    }

    private fun compileFunction(function: AstNode.Function<Type>) {
        functionEntries[function.name] = newBlock()
        val functionType = functions[function.name] ?: throw CompilationException("Function not found")
        for ((arg, type) in function.args.map { it.first }.zip(functionType.args).reversed()) {
            val param = registerVariable(arg, type)
            for (corresponding in param.mapped.reversed()) {
                code("pop $corresponding")
            }
        }
        compileBlock(function.body)
    }

    private fun compileBlock(block: AstNode.Block<Type>) {
        scopes.addFirst(mutableListOf())
        compileStatements(block.statements)
        scopes.removeFirst()
    }

    private fun compileStatements(statements: AstNode.Statements<Type>) {
        for (statement in statements.statements) {
            compileStatement(statement)
        }
    }

    private fun compileStatement(statement: AstNode.Statement<Type>) {
        when (statement) {
            is AstNode.Assignment -> compileAssignment(statement)
            is AstNode.Block -> compileBlock(statement)
            is AstNode.Declaration -> compileDeclaration(statement)
            is AstNode.DoWhile -> TODO()
            is AstNode.If -> TODO()
            is AstNode.Return -> TODO()
            is AstNode.Statements -> compileStatements(statement)
            is AstNode.While -> TODO()
            is AstNode.Expression -> {
                compileExpression(statement)
                code("pop")
            }
        }
    }

    private fun compileDeclaration(declaration: AstNode.Declaration<Type>) {
        val variable = registerVariable(declaration.name, declaration.extra, true)
        if (declaration.value != null) {
            compileExpression(declaration.value)
            for (mapped in variable.mapped.reversed()) {
                code("pop $mapped")
            }
        }
    }

    private fun compileAssignment(assignment: AstNode.Assignment<Type>) {
        val variable = getVariable(assignment.name)
        compileExpression(assignment.value)
        for (mapped in variable.mapped.reversed()) {
            code("pop $mapped")
        }
    }

    fun compileExpression(expression: AstNode.Expression<Type>) {
        return when (expression) {
            is AstNode.BinaryExpression -> compileBinaryExpression(expression)
            is AstNode.BooleanLiteral -> code(if (expression.value) "push 1" else "push 0")
            is AstNode.CharLiteral -> code("push ${expression.value.code}")
            is AstNode.FunctionCall -> TODO()
            is AstNode.MemberAccess -> TODO()
            is AstNode.NumberLiteral -> code("push ${expression.value}")
            is AstNode.StringLiteral -> TODO()
            is AstNode.UnaryExpression -> compileUnaryExpression(expression)
            is AstNode.Variable -> pushVariable(getVariable(expression.name))
        }
    }

    private fun compileBinaryExpression(expression: AstNode.BinaryExpression<Type>) {
        compileExpression(expression.left)
        compileExpression(expression.right)
        when (expression.operator) {
            BinOp.PLUS -> code("add { nop; }")
            BinOp.MINUS -> code("sub { nop; }")
            BinOp.TIMES -> code("mul { nop; }")
            BinOp.DIV -> code("div { nop; }")
            BinOp.MOD -> TODO()

            BinOp.EQ -> generateEq(expression.left.extra.size)
            BinOp.NEQ -> {
                generateEq(expression.left.extra.size)
                code("not { nop; }")
            }

            BinOp.LT -> code("lt { nop; }")
            BinOp.GT -> code("gt { nop; }")
            BinOp.LTE -> code("gt { nop; }", "not { nop; }")
            BinOp.GTE -> code("lt { nop; }", "not { nop; }")

            BinOp.AND -> code("and { nop; }")
            BinOp.OR -> code("or { nop; }")
        }
    }

    fun compileUnaryExpression(expression: AstNode.UnaryExpression<Type>) {
        compileExpression(expression.value)
        when (expression.operator) {
            UnOp.NEG -> code("neg { nop; }")
            UnOp.NOT -> code("not { nop; }")
            UnOp.DEREF -> TODO()
        }
    }

    fun registerVariable(name: String, type: Type, define: Boolean = false): Variable {
        val scope = scopes.first()
        if (scope.any { it.name == name }) {
            throw CompilationException("Variable $name already in scope")
        }
        val corresponding = (0 until type.size).map { "\$$name${scopes.size}p$it${randomString()}" }
        if (define) {
            for (mapped in corresponding) {
                code("def $mapped")
            }
        }
        val variable = Variable(name, type, corresponding)
        if (!allVars.addAll(corresponding)) throw AssertionError()
        scope.add(variable)
        return variable
    }

    fun unregisterVariable(name: String, delete: Boolean = false) {
        val scope = scopes.first()
        val variable = scope.firstOrNull { it.name == name } ?: throw CompilationException("Variable $name not found")
        scope.remove(variable)
        val mapped = variable.mapped
        if (delete) {
            for (corresponding in mapped) {
                code("del $corresponding;")
            }
        }
        allVars.removeAll(mapped.toSet())
    }

    fun getVariable(name: String): Variable {
        for (scope in scopes) {
            val variable = scope.firstOrNull { it.name == name }
            if (variable != null) return variable
        }
        throw CompilationException("Variable $name not found")
    }

    inline fun withTempVar(type: Type, block: (Variable) -> Unit) {
        val name = "temp${randomString()}"
        val variable = registerVariable(name, type, true)
        block(variable)
        unregisterVariable(name, true)
    }

    fun pushVariable(variable: Variable) {
        for (corresponding in variable.mapped) {
            code("push $corresponding")
        }
    }

    fun popVariable(variable: Variable) {
        for (corresponding in variable.mapped.reversed()) {
            code("pop $corresponding")
        }
    }

    fun setState(state: Int): String = "push $state; pop $stateVar;"

    fun newBlock(cont: Boolean = true): Int {
        if (code.isNotEmpty()) {
            if (cont) {
                code(setState(blocks.size + 1))
            }
            blocks.add(code.toString())
            code.clear()
        }
        return blocks.size
    }

    fun code(vararg lines: String) {
        for (line in lines) {
            code.append(line).appendLine(';')
        }
    }

    private fun generateEq(size: Int) {
        if (size == 1) {
            code("eq { nop; }")
        } else {
            val tempStack = "@eq${randomString()}"
            code("def $tempStack")
            withTempVar(Type.Primitive.BOOLEAN) { tempVar ->
                repeat(size) {
                    code(
                        "eq { nop; }",
                        "pop ${tempVar.mapped.single()}",
                        "$tempStack.push ${tempVar.mapped.single()}"
                    )
                }
            }
            repeat(size - 1) {
                code("$tempStack.and { nop; }")
            }
            withTempVar(Type.Primitive.BOOLEAN) { tempVar ->
                code("$tempStack.pop ${tempVar.mapped.single()}")
                code("push ${tempVar.mapped.single()}")
            }
            code("undef $tempStack")
        }
    }
}

fun randomString() = "t${Random.nextULong().toString(16)}"

class CompilationException(message: String? = null) : RuntimeException(message)