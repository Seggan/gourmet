package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.parsing.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.random.Random
import kotlin.random.nextULong

class Compiler(
    private val code: AstNode,
    private val externalFunctions: Set<String>
) {

    private val scopes = ArrayDeque<MutableMap<String, String>>().apply { addLast(mutableMapOf()) }

    val functions = mutableSetOf<String>()

    private val builtinCache = mutableMapOf<String, MethodHandle>()

    fun compile(): String {
        return compileNode(code)
    }

    private fun compileNode(node: AstNode): String {
        return when (node) {
            is AstNode.Application -> getBuiltin(node.fn).invoke(node.args) as String
            is AstNode.Boolean -> if (node.value) "push 1;" else "push 0;"
            is AstNode.Number -> "push ${node.value.stripTrailingZeros().toPlainString()};"
            is AstNode.String -> TODO()
            is AstNode.Symbol -> TODO()
            is AstNode.Vector -> TODO()
        }
    }

    private fun getBuiltin(name: String): MethodHandle = builtinCache.getOrPut(name) {
        try {
            val method = MethodHandles.lookup().findVirtual(
                Compiler::class.java,
                "builtin${name.first().uppercaseChar()}${name.drop(1)}",
                MethodType.methodType(String::class.java, Compiler::class.java, List::class.java)
            )
            method.bindTo(this)
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException("Unknown function: $name")
        }
    }

    private fun registerVariable(name: String, newScope: Boolean = false): String {
        val scope = if (newScope) mutableMapOf<String, String>().also(scopes::addLast) else scopes.last()
        if (name in scope) {
            throw CompilationException("Variable $name already in scope")
        }
        val corresponding = "\$$name${scopes.size}"
        scope[name] = corresponding
        return corresponding
    }

    private fun builtinDefn(args: List<AstNode>): String {
        val (name, argNames, code) = args
        functions += name.symbolValue()
        val argString = StringBuilder()
        for (arg in argNames.vectorValue()) {
            val argName = arg.symbolValue()
            registerVariable(argName)
            argString.append(argName).append(" ")
        }
        return """
            fun ${name.symbolValue()} ${argString.trim()} {
                ${compileNode(code)}
            }
        """.trimIndent()
    }
}

fun randomString() = "t${Random.nextULong().toString(16)}"

class CompilationException(message: String? = null) : RuntimeException(message)