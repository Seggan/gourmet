package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.parsing.AstNode

class TypeChecker(private val ast: AstNode<Unit>) {

    fun check(): TypedAst {
        return checkNode(ast)
    }

    fun checkNode(node: AstNode<Unit>): TypedAst {
        return when (node) {
            is AstNode.Application -> {
                val fn = checkNode(node.fn)
                val args = node.args.map { checkNode(it) }
                val fnType = fn.value
                if (fnType !is Type.Function) {
                    throw TypeException("Expected function, got $fnType")
                }
                if (fnType.args.size != args.size) {
                    throw TypeException("Expected ${fnType.args.size} arguments, got ${args.size}")
                }
                for ((arg, expected) in args.zip(fnType.args)) {
                    if (arg.value != expected) {
                        throw TypeException("Expected $expected, got ${arg.value}")
                    }
                }
                AstNode.Application(fn, args, fnType.ret)
            }
            is AstNode.Boolean -> AstNode.Boolean(node.value, Type.Primitive.BOOLEAN)
            is AstNode.NumberLiteral -> AstNode.NumberLiteral(node.value, Type.Primitive.NUMBER)
            is AstNode.String -> AstNode.String(node.value, Type.String)
            is AstNode.Symbol -> AstNode.Symbol(node.value, Type.Unknown)
            is AstNode.Array -> {
                val elements = node.elements.map { checkNode(it) }
                val elementType = elements.firstOrNull()?.value ?: Type.Unknown
                for (element in elements) {
                    if (element.value != elementType) {
                        throw TypeException("Expected $elementType, got ${element.value}")
                    }
                }
                AstNode.Array(elements, Type.Array(elementType))
            }
        }
}

typealias TypedAst = AstNode<Type>

class TypeException(message: String) : Exception(message)