package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.parsing.*
import kotlin.random.Random
import kotlin.random.nextULong

class Compiler(
    private val ast: TypedAst,
    private val externalFunctions: Set<String>
) {

    val functions = mutableSetOf<String>()

    private val code = StringBuilder()
    private val blocks = mutableListOf<String>()
    private val scopes = ArrayDeque<MutableList<Variable>>().apply { addLast(mutableListOf()) }

    private val stackStack = "@stack${randomString()}"

    private val builtins = mutableMapOf<String, (List<TypedAst>) -> Unit>()

    init {
        builtins["println"] = ::builtinPrintln
        builtins["let"] = ::builtinLet
    }

    fun compile(): String {
        compileNode(ast)
        if (code.isNotEmpty()) {
            blocks.add(code.toString())
        }
        val main = StringBuilder()
        val stateVar = "\$state${randomString()}"
        main.appendLine("def $stateVar 0;")
        main.appendLine("while 1 {")
        for ((i, block) in blocks.withIndex()) {
            main.appendLine("eq $stateVar $i;")
            main.appendLine("if {")
            main.appendLine(block)
            main.appendLine("};")
        }
        main.appendLine("};")
        return main.toString()
    }

    private fun compileNode(node: TypedAst) {
        when (node) {
            is AstNode.Application -> builtins[node.fn]!!(node.args)
            is AstNode.Boolean -> code(if (node.value) "push 1" else "push 0")
            is AstNode.Number -> code("push ${node.value.stripTrailingZeros().toPlainString()}")
            is AstNode.String -> TODO()
            is AstNode.Symbol -> TODO()
            is AstNode.Array -> TODO()
        }
    }

    private fun pushScope() {
        val lastScope = scopes.lastOrNull()
        if (lastScope != null) {
            for (variable in lastScope) {
                for (mapped in variable.mapped) {
                    code("$stackStack.push $mapped", "del $mapped")
                }
            }
        }
        scopes.addLast(mutableListOf())
    }

    private fun popScope() {
        val lastScope = scopes.removeLast()
        for (variable in lastScope) {
            for (mapped in variable.mapped) {
                code("del $mapped")
            }
        }
        val previousScope = scopes.lastOrNull() ?: return
        for (variable in previousScope.reversed()) {
            for (mapped in variable.mapped.reversed()) {
                code("def $mapped", "$stackStack.pop $mapped")
            }
        }
    }

    private fun registerVariable(name: String, size: Int): Variable {
        val scope = scopes.last()
        if (scope.any { it.name == name }) {
            throw CompilationException("Variable $name already in scope")
        }
        val corresponding = (0 until size).map { "\$$name${scopes.size}p$it${randomString()}" }
        for (mapped in corresponding) {
            code("def $mapped")
        }
        val variable = Variable(name, corresponding)
        scopes.last().add(variable)
        return variable
    }

    private fun builtinLet(args: List<TypedAst>) {
        val (vars, code) = args
    }

    private fun builtinPrintln(args: List<TypedAst>) {
        compileNode(args.single())
        code("print", "flush")
    }

    private fun code(vararg lines: String) {
        for (line in lines) {
            code.append(line).appendLine(';')
        }
    }
}

private fun randomString() = "t${Random.nextULong().toString(16)}"

class CompilationException(message: String? = null) : RuntimeException(message)